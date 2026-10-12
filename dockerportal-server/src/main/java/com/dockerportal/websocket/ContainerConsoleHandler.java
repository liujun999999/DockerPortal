package com.dockerportal.websocket;

import com.dockerportal.docker.DockerClientManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerConsoleHandler extends AbstractWebSocketHandler {

    private final DockerClientManager dockerClientManager;
    private final ObjectMapper objectMapper;
    private final WsAuthChecker wsAuthChecker;
    private final Map<String, PipedOutputStream> pipeOuts = new ConcurrentHashMap<>();
    private final Map<String, ExecSession> execSessions = new ConcurrentHashMap<>();

    private record ExecSession(DockerClient client, String execId) {}

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 容器终端 = exec 进容器，需 OPERATOR 及以上
        String role = wsAuthChecker.resolveRole(session);
        if (wsAuthChecker.closeIfUnauthorized(session, role, "ADMIN", "OPERATOR")) {
            return;
        }
        var params = extractParams(session);
        Long envId = Long.parseLong(params.get("envId"));
        String containerId = params.get("containerId");

        DockerClient client = dockerClientManager.getClient(envId);

        // 优先使用 bash，镜像内无 bash 时回退到 sh
        String execId;
        try {
            execId = createExec(client, containerId, "/bin/bash");
        } catch (Exception e) {
            log.info("bash not available in container {}, falling back to /bin/sh", containerId);
            execId = createExec(client, containerId, "/bin/sh");
        }

        execSessions.put(session.getId(), new ExecSession(client, execId));

        // Create pipe for stdin
        PipedInputStream pipedIn = new PipedInputStream();
        PipedOutputStream pipedOut = new PipedOutputStream(pipedIn);

        client.execStartCmd(execId)
                .withStdIn(pipedIn)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        try {
                            if (session.isOpen()) {
                                session.sendMessage(new BinaryMessage(frame.getPayload()));
                            }
                        } catch (IOException e) {
                            log.error("Error sending console output", e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        // 不记日志的话前端只能看到一句无信息量的 Disconnected.，把真实原因写回终端
                        String cause = error == null ? "未知错误" : String.valueOf(error);
                        log.warn("Console stream broke for container {}: {}", containerId, cause);
                        writeNotice(session, "\r\n\u001b[31m终端连接已中断：" + cause + "\u001b[0m\r\n");
                        closeQuietly(session);
                    }

                    @Override
                    public void onComplete() {
                        log.info("Console session completed for container: {}", containerId);
                        try {
                            if (session.isOpen()) session.close(CloseStatus.NORMAL);
                        } catch (IOException ignored) {}
                    }
                });

        pipeOuts.put(session.getId(), pipedOut);
        log.info("Console session started for container: {}", containerId);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        // 二进制帧 = 终端按键输入，写入 shell stdin
        PipedOutputStream pipeOut = pipeOuts.get(session.getId());
        if (pipeOut != null) {
            pipeOut.write(message.getPayload().array());
            pipeOut.flush();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, org.springframework.web.socket.TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload.isEmpty()) return;
        // 兼容旧版前端：控制消息可能带 NUL 前缀
        String body = payload.charAt(0) == '\u0000' ? payload.substring(1) : payload;
        if (body.charAt(0) == '{') {
            try {
                var node = objectMapper.readTree(body);
                if ("resize".equals(node.path("type").asText())) {
                    doResize(session, node);
                    return;
                }
            } catch (Exception ignored) {
                // 非合法 JSON，当作普通按键输入
            }
        }
        // 兼容旧版前端：文本帧按键写入 stdin（新版前端按键走二进制帧）
        PipedOutputStream pipeOut = pipeOuts.get(session.getId());
        if (pipeOut != null) {
            pipeOut.write(payload.getBytes());
            pipeOut.flush();
        }
    }

    private void doResize(WebSocketSession session, com.fasterxml.jackson.databind.JsonNode node) {
        ExecSession es = execSessions.get(session.getId());
        if (es == null) return;
        try {
            int cols = node.path("cols").asInt(80);
            int rows = node.path("rows").asInt(24);
            es.client().resizeExecCmd(es.execId()).withSize(rows, cols).exec();
            log.debug("Console resized: exec={} rows={} cols={}", es.execId(), rows, cols);
        } catch (Exception e) {
            log.warn("Failed to resize console: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Console connection closed: {} status={}", session.getUri(), status);
        execSessions.remove(session.getId());
        PipedOutputStream pipeOut = pipeOuts.remove(session.getId());
        if (pipeOut != null) {
            try { pipeOut.close(); } catch (IOException ignored) {}
        }
    }

    private void writeNotice(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new org.springframework.web.socket.TextMessage(text));
            }
        } catch (Exception e) {
            log.debug("Failed to notify console client: {}", e.getMessage());
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {}
    }

    private String createExec(DockerClient client, String containerId, String shell) {
        return client.execCreateCmd(containerId)
                .withCmd(shell)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .exec()
                .getId();
    }

    private Map<String, String> extractParams(WebSocketSession session) {
        String path = session.getUri().getPath();
        UriTemplate template = new UriTemplate("/ws/env/{envId}/containers/{containerId}/console");
        return template.match(path);
    }
}
