package com.dockerportal.websocket;

import com.dockerportal.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.Arrays;

/**
 * WebSocket 握手鉴权辅助：从查询串解析 JWT，校验有效性并做角色判断。
 * 浏览器 WebSocket API 无法设置请求头，故 token 通过 ?token= 传递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsAuthChecker {

    private final JwtTokenProvider jwtTokenProvider;

    /** 校验通过返回角色名（ADMIN/OPERATOR/VIEWER），否则返回 null */
    public String resolveRole(WebSocketSession session) {
        String token = extractToken(session);
        if (!StringUtils.hasText(token) || !jwtTokenProvider.validateToken(token)) {
            return null;
        }
        // 待强制改密的用户不允许建立终端/流式会话
        if (jwtTokenProvider.getMustChangeFromToken(token)) {
            return null;
        }
        return jwtTokenProvider.getRoleFromToken(token);
    }

    /** 是否具备给定角色之一 */
    public boolean hasAnyRole(String role, String... allowed) {
        if (role == null) return false;
        return Arrays.asList(allowed).contains(role);
    }

    /** 校验通过返回登录用户名（供审计使用），否则返回 null */
    public String resolveUsername(WebSocketSession session) {
        String token = extractToken(session);
        if (!StringUtils.hasText(token) || !jwtTokenProvider.validateToken(token)) {
            return null;
        }
        return jwtTokenProvider.getUsernameFromToken(token);
    }

    /** 无权限时关闭连接并回传原因，返回 true 表示已拒绝 */
    public boolean closeIfUnauthorized(WebSocketSession session, String role, String... allowedRoles) throws Exception {
        if (hasAnyRole(role, allowedRoles)) {
            return false;
        }
        log.warn("WebSocket 访问被拒绝: role={}, uri={}", role, session.getUri());
        if (session.isOpen()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("无权访问该终端"));
        }
        return true;
    }

    private String extractToken(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String query = uri.getQuery();
        if (!StringUtils.hasText(query)) return null;
        return Arrays.stream(query.split("&"))
                .map(kv -> kv.split("=", 2))
                .filter(kv -> kv.length == 2 && "token".equals(kv[0]))
                .map(kv -> kv[1])
                .findFirst()
                .orElse(null);
    }
}
