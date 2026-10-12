package com.dockerportal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import com.dockerportal.websocket.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private ContainerLogHandler containerLogHandler;

    @Autowired
    private ContainerConsoleHandler containerConsoleHandler;

    @Autowired
    private MonitorHandler monitorHandler;

    @Autowired
    private SshTerminalHandler sshTerminalHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(containerLogHandler, "/ws/env/{envId}/containers/{containerId}/logs")
                .setAllowedOrigins("*");
        registry.addHandler(containerConsoleHandler, "/ws/env/{envId}/containers/{containerId}/console")
                .setAllowedOrigins("*");
        registry.addHandler(monitorHandler, "/ws/env/{envId}/monitor")
                .setAllowedOrigins("*");
        registry.addHandler(sshTerminalHandler, "/ws/env/{envId}/ssh/terminal/{target}")
                .setAllowedOrigins("*");
    }
}
