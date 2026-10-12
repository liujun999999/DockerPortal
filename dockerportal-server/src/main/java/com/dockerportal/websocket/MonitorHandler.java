package com.dockerportal.websocket;

import com.dockerportal.docker.DockerClientManager;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Statistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorHandler extends TextWebSocketHandler {

    private final DockerClientManager dockerClientManager;
    private final ObjectMapper objectMapper;
    private final WsAuthChecker wsAuthChecker;
    private final Map<String, ResultCallback<Statistics>> callbacks = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 监控为只读，任意已登录角色均可，但必须携带有效 token
        if (wsAuthChecker.resolveRole(session) == null) {
            if (session.isOpen()) session.close(CloseStatus.POLICY_VIOLATION.withReason("未授权"));
            return;
        }
        var params = extractParams(session);
        Long envId = Long.parseLong(params.get("envId"));

        DockerClient client = dockerClientManager.getClient(envId);
        // Will wait for client to send container ID
        log.info("Monitor WebSocket connected for env: {}", envId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String containerId = message.getPayload();
        Long envId = Long.parseLong(extractParams(session).get("envId"));

        // Close existing callback
        ResultCallback<Statistics> existing = callbacks.remove(session.getId());
        if (existing != null) {
            try { existing.close(); } catch (IOException ignored) {}
        }

        DockerClient client = dockerClientManager.getClient(envId);
        ResultCallback<Statistics> callback = client.statsCmd(containerId)
                .exec(new ResultCallback.Adapter<Statistics>() {
                    @Override
                    public void onNext(Statistics stats) {
                        try {
                            if (session.isOpen()) {
                                String json = objectMapper.writeValueAsString(stats);
                                session.sendMessage(new TextMessage(json));
                            }
                        } catch (IOException e) {
                            log.error("Error sending stats", e);
                        }
                    }
                });

        callbacks.put(session.getId(), callback);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ResultCallback<Statistics> callback = callbacks.remove(session.getId());
        if (callback != null) {
            try { callback.close(); } catch (IOException ignored) {}
        }
    }

    private Map<String, String> extractParams(WebSocketSession session) {
        String path = session.getUri().getPath();
        UriTemplate template = new UriTemplate("/ws/env/{envId}/monitor");
        return template.match(path);
    }
}
