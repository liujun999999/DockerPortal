package com.dockerportal.config;

import com.dockerportal.model.entity.User;
import com.dockerportal.model.enums.UserRole;
import com.dockerportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String DEFAULT_JWT_SECRET = "dockerportal-default-secret-key-change-in-production-2024";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${dockerportal.jwt.secret:}")
    private String jwtSecret;

    @Value("${dockerportal.setup.enabled:true}")
    private boolean setupEnabled;

    @Value("${server.port:9800}")
    private int serverPort;

    @Override
    public void run(String... args) {
        if (jwtSecret == null || jwtSecret.isBlank() || DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            log.warn("!!! 安全警告：JWT 密钥正在使用内置默认值，公网/共享环境部署请通过 DP_JWT_SECRET 环境变量设置随机密钥，否则登录凭证可被伪造");
        }
        if (userRepository.count() > 0) {
            return;
        }
        // 唯一保留的自动建号分支：IaC/自动化部署显式给了密码，无需人进浏览器
        String initialPassword = System.getenv("DP_ADMIN_PASSWORD");
        if (initialPassword != null && !initialPassword.isBlank()) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode(initialPassword))
                    .email("admin@dockerportal.local")
                    .role(UserRole.ADMIN)
                    .enabled(true)
                    .mustChangePassword(true)
                    .build();
            userRepository.save(admin);
            log.info("初始管理员已创建: admin（密码来自 DP_ADMIN_PASSWORD，首次登录将强制修改）");
            return;
        }
        // 不再随机生成密码写日志：docker run 场景下日志滚走即无法自救，改由浏览器安装向导接管
        if (setupEnabled) {
            log.warn("=======================================================================");
            log.warn(" 实例尚未初始化（无任何用户账号）。");
            log.warn(" 请用浏览器打开 http://<本机地址>:{} 进入安装向导创建管理员账号。", serverPort);
            log.warn(" 无浏览器环境可用 DP_ADMIN_PASSWORD 环境变量指定初始密码后重启。");
            log.warn("=======================================================================");
        } else {
            log.error("实例无用户账号且初始化入口已被 DP_SETUP_ENABLED=false 关闭，无法登录任何账号；"
                    + "请恢复该开关完成初始化，或在无人值守场景设置 DP_ADMIN_PASSWORD 后重启。");
        }
    }
}
