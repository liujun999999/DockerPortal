package com.dockerportal.service;

import com.dockerportal.config.SetupKeyEnvironmentPostProcessor;
import com.dockerportal.exception.BusinessException;
import com.dockerportal.model.dto.SetupRequest;
import com.dockerportal.model.dto.SetupStatusDTO;
import com.dockerportal.model.dto.UserDTO;
import com.dockerportal.repository.UserRepository;
import com.dockerportal.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 首次安装：判断实例是否待初始化，并在浏览器里完成管理员账号创建。
 * <p>
 * 初始化入口是匿名可访问的，安全性靠三条闸门兜住：
 * 1. {@code userRepository.count() == 0} 的服务端硬校验，装完即永久拒绝；
 * 2. DP_SETUP_ENABLED 运维开关，装完可彻底关掉入口；
 * 3. Origin/Referer 同站校验 —— 项目全局 {@code csrf.disable()}，缺了这一步，
 *    任意恶意页面都能驱动浏览器向本机 POST 建号，把攻击者变成管理员。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SetupService {

    /** 用户名要进 shell 拼接（SSH 相关链路）与日志，收紧到不易混淆的字符集 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{3,32}$");
    private static final String DEFAULT_USERNAME = "admin";

    /** 初始化入口专用错误码。40310 已被 JwtAuthFilter 的"待强制改密"语义占用且前端对其有特判，这里避开 */
    private static final int CODE_ALREADY_INITIALIZED = 40320;
    private static final int CODE_SETUP_DISABLED = 40321;
    private static final int CODE_CROSS_ORIGIN = 40322;

    private final UserRepository userRepository;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;

    @Value("${dockerportal.setup.enabled:true}")
    private boolean setupEnabled;

    @Value("${" + SetupKeyEnvironmentPostProcessor.PROP_FRESH_INSTALL + ":false}")
    private boolean freshInstall;

    public SetupStatusDTO getStatus() {
        boolean needsSetup = setupEnabled && userRepository.count() == 0;
        return SetupStatusDTO.builder()
                .needsSetup(needsSetup)
                .freshInstall(freshInstall)
                .setupEnabled(setupEnabled)
                .build();
    }

    /** 初始化成功即签发令牌，前端免二次登录 */
    public Map<String, Object> initialize(SetupRequest request, HttpServletRequest http) {
        if (userRepository.count() > 0) {
            throw new BusinessException(CODE_ALREADY_INITIALIZED, "系统已完成初始化");
        }
        if (!setupEnabled) {
            throw new BusinessException(CODE_SETUP_DISABLED, "初始化入口已关闭（DP_SETUP_ENABLED=false）");
        }
        verifySameOrigin(http);

        String username = request.getUsername() == null || request.getUsername().isBlank()
                ? DEFAULT_USERNAME
                : request.getUsername().trim();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(40014, "用户名只能包含字母、数字、下划线、点、横线，长度 3-32");
        }

        UserDTO admin = userService.createInitialAdmin(username, request.getPassword());
        String ip = clientIp(http);
        auditService.record(admin.getId(), admin.getUsername(), "SYSTEM_INITIALIZE", "system",
                String.valueOf(admin.getId()), null, "首次安装：创建管理员账号 " + admin.getUsername(), ip);
        log.warn("系统已完成初始化：管理员 {}，来源 {}", admin.getUsername(), ip);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", jwtTokenProvider.generateAccessToken(admin.getUsername(), admin.getRole().name()));
        result.put("username", admin.getUsername());
        return result;
    }

    /**
     * 带 Origin 或 Referer 时，其 host 必须与请求 Host 一致；两者都缺失时放行
     * （curl、原生 App 等非浏览器客户端不发这两个头）。
     */
    private void verifySameOrigin(HttpServletRequest http) {
        String origin = http.getHeader("Origin");
        String referer = http.getHeader("Referer");
        String source = origin != null ? origin : referer;
        if (source == null || source.isBlank()) {
            return;
        }
        String expected = http.getHeader("Host");
        String actual = hostOf(source);
        if (actual == null || expected == null || !actual.equalsIgnoreCase(expected)) {
            throw new BusinessException(CODE_CROSS_ORIGIN, "跨站请求已被拒绝：初始化只能从本机部署的页面发起");
        }
    }

    /** 从 Origin/Referer 里取出 host:port，解析失败当作 null 处理（走拒绝分支） */
    private String hostOf(String url) {
        try {
            return new java.net.URI(url).getAuthority();
        } catch (Exception e) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
