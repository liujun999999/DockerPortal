package com.dockerportal.exception;

import com.dockerportal.docker.DockerClientManager;
import com.dockerportal.model.dto.ApiResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.LocalNodeState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final DockerClientManager dockerClientManager;

    private static final Pattern ENV_ID_PATTERN = Pattern.compile("/env/(\\d+)/");

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(40000, message));
    }

    /** 参数拼装错误（非法枚举/数字解析等）属于客户端问题，返 40000 而非 500 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(40000, e.getMessage() == null ? "参数不合法" : e.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(40100, "用户名或密码错误"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(40300, "权限不足"));
    }

    /**
     * Docker Swarm 相关错误转换为友好引导码：
     * 40901 = 节点未加入集群（可引导初始化），40902 = Worker 节点（无 manager 权限），
     * 40903 = 集群已初始化但 Manager 被锁定（需解锁密钥）。
     * Docker 对不同状态的报错文字相似，不可靠；以 info 接口的 LocalNodeState 为准判定。
     */
    @ExceptionHandler(DockerException.class)
    public ResponseEntity<ApiResponse<Void>> handleDockerException(DockerException e, HttpServletRequest request) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        log.warn("Docker API error: {}", msg);
        if (msg.contains("swarm")) {
            int code = classifySwarmError(request, msg);
            return switch (code) {
                case 40902 -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(40902,
                        "当前节点是 Swarm Worker 节点，无集群管理权限。请在 Manager 节点获取令牌后加入集群，或切换到 Swarm Manager 所在的环境。"));
                case 40903 -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(40903,
                        "当前 Swarm 集群的 Manager 已被锁定，需要输入解锁密钥（docker swarm unlock）后才能管理。"));
                default -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(40901,
                        "当前 Docker 环境尚未初始化 Swarm 集群，可在此页面一键初始化。"));
            };
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(50002, "Docker API 调用失败：" + msg));
    }

    /**
     * 从请求 URI 解析 envId，查当前环境的 LocalNodeState；失败则回退到文字匹配。
     */
    private int classifySwarmError(HttpServletRequest request, String msg) {
        Matcher m = ENV_ID_PATTERN.matcher(request.getRequestURI());
        if (m.find()) {
            try {
                var swarm = dockerClientManager.getClient(Long.valueOf(m.group(1))).infoCmd().exec().getSwarm();
                LocalNodeState state = swarm == null ? null : swarm.getLocalNodeState();
                if (state == LocalNodeState.LOCKED) return 40903;
                // docker-java 枚举无 JOINED，worker 节点报 PENDING；未映射状态为 null 走下方文字回退
                if (state == LocalNodeState.PENDING) return 40902;
                if (state == LocalNodeState.INACTIVE || state == LocalNodeState.EMPTY) return 40901;
            } catch (Exception ex) {
                log.debug("Cannot resolve swarm state, fallback to message matching", ex);
            }
        }
        if (msg.contains("Error 3: This node is not part of a swarm")) return 40902;
        return 40901;
    }

    /**
     * docker-java 的 404（资源不存在）单独处理，避免被 DockerException 处理器归为网关错误。
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDockerNotFound(NotFoundException e) {
        log.warn("Docker resource not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(40400, "Docker 资源不存在：" + (e.getMessage() == null ? "" : e.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(50000, "服务器内部错误"));
    }
}
