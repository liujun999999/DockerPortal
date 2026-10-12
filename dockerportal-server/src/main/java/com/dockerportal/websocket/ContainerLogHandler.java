package com.dockerportal.websocket;

import com.dockerportal.docker.DockerClientManager;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerLogHandler extends TextWebSocketHandler {

    private final DockerClientManager dockerClientManager;
    private final WsAuthChecker wsAuthChecker;
    private final Map<String, ResultCallback<Frame>> callbacks = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 日志流为只读，任意已登录角色均可，但必须携带有效 token
        if (wsAuthChecker.resolveRole(session) == null) {
            if (session.isOpen()) session.close(CloseStatus.POLICY_VIOLATION.withReason("未授权"));
            return;
        }
        var params = extractParams(session);
        Long envId = Long.parseLong(params.get("envId"));
        String containerId = params.get("containerId");

        DockerClient client = dockerClientManager.getClient(envId);

        ResultCallback<Frame> callback = client.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withTail(100)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        try {
                            if (session.isOpen()) {
                                session.sendMessage(new TextMessage(new String(frame.getPayload())));
                            }
                        } catch (IOException e) {
                            log.error("Error sending log message", e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        // follow 流被掐断时若不关 WebSocket，前端的「实时跟踪」会停在开启态却再也不出字，
                        // 给出错误信息并断开，让界面状态回到真实
                        String cause = error == null ? "未知错误" : String.valueOf(error);
                        log.warn("Log stream broke for container {}: {}", containerId, cause);
                        try {
                            if (session.isOpen()) {
                                session.sendMessage(new TextMessage("\r\n[日志流中断：" + cause + "]\r\n"));
                            }
                        } catch (Exception ignored) {
                            // 客户端已断开，无需再报
                        }
                        closeQuietly(session);
                    }

                    @Override
                    public void onComplete() {
                        log.info("Log stream completed for container: {}", containerId);
                        closeQuietly(session);
                    }
                });

        callbacks.put(session.getId(), callback);
        log.info("Log stream started for container: {}", containerId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Log connection closed: {} status={}", session.getUri(), status);
        ResultCallback<Frame> callback = callbacks.remove(session.getId());
        if (callback != null) {
            try {
                callback.close();
            } catch (IOException e) {
                log.warn("Error closing log callback", e);
            }
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {}
    }

    private Map<String, String> extractParams(WebSocketSession session) {
        String path = session.getUri().getPath();
        UriTemplate template = new UriTemplate("/ws/env/{envId}/containers/{containerId}/logs");
        return template.match(path);
    }
}
