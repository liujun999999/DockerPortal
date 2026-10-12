package com.dockerportal.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * 首次安装的密钥与状态准备。
 * <p>
 * 必须跑在 Bean 初始化之前：{@link com.dockerportal.security.JwtTokenProvider} 在构造期就把
 * secret 转成 SecretKey，任何比它晚的组件都来不及影响它。而"数据目录是否为空"这一判定也只能在此刻做
 * —— {@link com.dockerportal.service.SecretCipher} 与 H2 随后就会往该目录写入 .secret.key 与
 * dockerportal.mv.db，之后再无从分辨全新安装与已装实例。
 * <p>
 * 密钥解析优先级：DP_JWT_SECRET 环境变量 &gt; 数据目录下 .jwt.key &gt; 全新安装时随机生成并落盘
 * &gt; 沿用 application.yml 的兜底值（此时由 DataInitializer 打告警）。
 * <p>
 * 刻意只为全新安装生成密钥：已部署实例若被自动换钥，所有在线会话会当场失效，这个决定权留给运维。
 */
@Order(ConfigDataEnvironmentPostProcessor.ORDER + 10)
public class SetupKeyEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** 供 SetupService / DataInitializer 用 @Value 读取 */
    public static final String PROP_FRESH_INSTALL = "dockerportal.setup.fresh-install";

    private static final String PROP_JWT_SECRET = "dockerportal.jwt.secret";
    private static final String PROP_DATA_PATH = "dockerportal.data-path";
    /** 直接读该键而非 PROP_JWT_SECRET：yml 里它带了兜底默认值，取出来无法区分"运维显式设置"与"没人设置" */
    private static final String ENV_JWT_SECRET = "DP_JWT_SECRET";
    private static final String JWT_KEY_FILE = ".jwt.key";
    private static final String PROPERTY_SOURCE_NAME = "dockerportalSetup";
    /** 48 字节随机数，Base64 后 64 字符，足够 HS512 使用 */
    private static final int KEY_BYTES = 48;

    /**
     * EPP 跑在日志系统初始化之前，用普通 logger 打的语句会被丢弃。DeferredLog 会缓存到
     * 日志就绪后回放，这是 ConfigDataEnvironmentPostProcessor 自身的做法。注意它是
     * commons-logging 的 Log，没有 {} 占位符，拼串要自己写。
     */
    private final Log log;

    public SetupKeyEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(SetupKeyEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> overrides = new HashMap<>();
        try {
            String dataPath = environment.getProperty(PROP_DATA_PATH, "./data");
            boolean freshInstall = isFreshInstall(dataPath);
            overrides.put(PROP_FRESH_INSTALL, freshInstall);

            String secret = resolveSecret(environment, dataPath, freshInstall);
            if (secret != null) {
                overrides.put(PROP_JWT_SECRET, secret);
            }
        } catch (Exception e) {
            // 密钥没落地就继续启动，等于让 JwtTokenProvider 退回内置默认值，宁可起不来
            throw new IllegalStateException("初始化安装状态与 JWT 密钥失败", e);
        }

        if (!overrides.isEmpty()) {
            // addFirst：盖掉 application.yml 里那个仅作兜底告警用的默认 secret
            PropertySource<?> source = new MapPropertySource(PROPERTY_SOURCE_NAME, overrides);
            environment.getPropertySources().addFirst(source);
        }
    }

    /** 返回需要覆盖的密钥；null 表示沿用 application.yml 的解析结果 */
    private String resolveSecret(ConfigurableEnvironment environment, String dataPath, boolean freshInstall)
            throws IOException {
        String fromEnv = trimToNull(environment.getProperty(ENV_JWT_SECRET));
        if (fromEnv != null) {
            log.info("JWT 密钥取自 " + ENV_JWT_SECRET + " 环境变量，不会生成 " + JWT_KEY_FILE);
            return null; // yml 已按该变量解析，无需覆盖
        }

        Path keyFile = Paths.get(dataPath, JWT_KEY_FILE);
        if (Files.isRegularFile(keyFile)) {
            String stored = trimToNull(Files.readString(keyFile));
            if (stored != null) {
                return stored; // 装完后每次启动都走这里，必须覆盖，否则会退回内置默认值
            }
            log.warn(keyFile + " 内容为空，将重新生成密钥（旧令牌届时全部失效）");
        }

        if (!freshInstall) {
            // 已装实例不自动换钥：那会让在线会话当场失效，只告警，由运维决定是否设置环境变量
            log.warn("未配置 " + ENV_JWT_SECRET + "，且 " + keyFile + " 不存在；当前实例使用内置默认密钥，"
                    + "该值公开可见、登录凭证可被伪造，请尽快设置环境变量后重启");
            return null;
        }

        String generated = generateSecret();
        writeKeyFile(keyFile, generated);
        log.info("全新安装：JWT 签名密钥已随机生成并写入 " + keyFile + "（权限 600，请与数据库文件一同备份）");
        return generated;
    }

    /** 目录不存在，或存在但没有任何文件 —— 视为全新安装 */
    private boolean isFreshInstall(String dataPath) throws IOException {
        Path dir = Paths.get(dataPath);
        if (!Files.isDirectory(dir)) {
            return true;
        }
        try (var files = Files.list(dir)) {
            return files.findAny().isEmpty();
        }
    }

    private String generateSecret() {
        byte[] buffer = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(buffer);
        return Base64.getEncoder().encodeToString(buffer);
    }

    private void writeKeyFile(Path keyFile, String secret) throws IOException {
        Files.createDirectories(keyFile.getParent());
        Files.writeString(keyFile, secret);
        try {
            Files.setPosixFilePermissions(keyFile,
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // 非 POSIX 文件系统尽力而为，与 SecretCipher 的处理保持一致
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        // compose 的 VAR=${VAR:-} 形式在变量缺失时传的是空串而非未定义，
        // 不归一的话 Keys.hmacShaKeyFor(空) 会在构造期抛异常导致应用起不来
        return trimmed.isEmpty() ? null : trimmed;
    }
}
