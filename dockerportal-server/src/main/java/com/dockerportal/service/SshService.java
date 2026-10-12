package com.dockerportal.service;

import com.dockerportal.exception.BusinessException;
import com.dockerportal.model.entity.SshConfig;
import com.dockerportal.model.enums.SshAuthType;
import com.dockerportal.repository.SshConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.List;
import java.util.Objects;

/**
 * SSH 配置管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SshService {

    private final SshConfigRepository sshConfigRepository;
    private final SecretCipher secretCipher;

    /**
     * 启动时把库中残留的历史明文凭据补加密（幂等，已加密的跳过）
     */
    @EventListener(ApplicationReadyEvent.class)
    public void migratePlainSecrets() {
        int migrated = 0;
        for (SshConfig config : sshConfigRepository.findAll()) {
            String encryptedPw = secretCipher.encrypt(config.getPassword());
            String encryptedPk = secretCipher.encrypt(config.getPrivateKey());
            if (!Objects.equals(encryptedPw, config.getPassword()) || !Objects.equals(encryptedPk, config.getPrivateKey())) {
                config.setPassword(encryptedPw);
                config.setPrivateKey(encryptedPk);
                sshConfigRepository.save(config);
                migrated++;
            }
        }
        if (migrated > 0) {
            log.info("已为 {} 条历史 SSH 配置的明文凭据补加密", migrated);
        }
    }

    public List<SshConfig> listConfigs(Long envId) {
        List<SshConfig> configs = sshConfigRepository.findByEnvironmentId(envId);
        configs.forEach(this::maskSecret);
        return configs;
    }

    public SshConfig getConfig(Long id) {
        SshConfig config = sshConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "SSH 配置不存在"));
        return maskSecret(config);
    }

    /**
     * 按 Swarm 节点查询配置（供终端连接使用，凭据解密为明文，不脱敏）
     */
    public SshConfig getConfigByNode(Long envId, String nodeId) {
        SshConfig config = sshConfigRepository.findByEnvironmentId(envId).stream()
                .filter(c -> nodeId != null && nodeId.equals(c.getNodeId()))
                .findFirst()
                .orElse(null);
        if (config != null) decryptSecrets(config);
        return config;
    }

    /**
     * 按配置 ID 查询（供终端连接使用，凭据解密为明文，不脱敏），校验环境归属
     */
    public SshConfig getConfigForConnect(Long envId, Long id) {
        SshConfig config = sshConfigRepository.findById(id).orElse(null);
        if (config == null || !config.getEnvironmentId().equals(envId)) {
            return null;
        }
        decryptSecrets(config);
        return config;
    }

    public SshConfig createConfig(SshConfig config) {
        validate(config, true);
        if (isNotBlank(config.getNodeId())
                && getConfigByNode(config.getEnvironmentId(), config.getNodeId()) != null) {
            throw new BusinessException(40900, "该节点已有 SSH 配置，请直接编辑");
        }
        config.setPassword(secretCipher.encrypt(config.getPassword()));
        config.setPrivateKey(secretCipher.encrypt(config.getPrivateKey()));
        SshConfig saved = sshConfigRepository.save(config);
        log.info("SSH config created for env {}: {}@{}:{} (node={}, custom={})", saved.getEnvironmentId(),
                saved.getUsername(), saved.getHost(), saved.getPort(), saved.getNodeId(),
                isBlank(saved.getNodeId()));
        return maskSecret(saved);
    }

    public SshConfig updateConfig(Long id, SshConfig updated) {
        SshConfig existing = sshConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "SSH 配置不存在"));
        if (updated.getHost() != null) existing.setHost(updated.getHost().trim());
        if (updated.getPort() != null) existing.setPort(updated.getPort());
        if (updated.getUsername() != null) existing.setUsername(updated.getUsername().trim());
        if (updated.getName() != null) existing.setName(updated.getName().trim());
        if (updated.getNodeId() != null) existing.setNodeId(updated.getNodeId());
        if (updated.getAuthType() != null) existing.setAuthType(updated.getAuthType());
        // 凭据留空表示不修改（列表接口不回传密码）；新值加密后覆盖，旧值已是密文（encrypt 幂等）
        if (isNotBlank(updated.getPassword())) existing.setPassword(updated.getPassword());
        if (isNotBlank(updated.getPrivateKey())) existing.setPrivateKey(updated.getPrivateKey());
        validate(existing, false);
        existing.setPassword(secretCipher.encrypt(existing.getPassword()));
        existing.setPrivateKey(secretCipher.encrypt(existing.getPrivateKey()));
        SshConfig saved = sshConfigRepository.save(existing);
        return maskSecret(saved);
    }

    public void deleteConfig(Long id) {
        sshConfigRepository.deleteById(id);
        log.info("SSH config deleted: {}", id);
    }

    /**
     * 测试 SSH 连通性：只建连 + 认证，不开 shell 通道。
     * 传入的是表单当前值；编辑时凭据留空表示沿用已保存的密码/私钥。
     * 成功返回欢迎信息，失败抛 BusinessException（携带用户可读的原因）。
     */
    public String testConnection(SshConfig req) {
        // 落地有效凭据：留空且已有 id（编辑）时沿用库中保存的凭据（解密后使用）
        String password = req.getPassword();
        String privateKey = req.getPrivateKey();
        if (req.getId() != null && (isBlank(password) || isBlank(privateKey))) {
            SshConfig stored = sshConfigRepository.findById(req.getId()).orElse(null);
            if (stored != null) {
                if (isBlank(password)) password = secretCipher.decrypt(stored.getPassword());
                if (isBlank(privateKey)) privateKey = secretCipher.decrypt(stored.getPrivateKey());
            }
        }
        if (isBlank(req.getHost())) throw new BusinessException(40000, "主机地址不能为空");
        if (isBlank(req.getUsername())) throw new BusinessException(40000, "登录用户名不能为空");
        int port = req.getPort() == null || req.getPort() <= 0 ? 22 : req.getPort();
        SshAuthType authType = req.getAuthType() == null ? SshAuthType.PASSWORD : req.getAuthType();
        if (authType == SshAuthType.PASSWORD && isBlank(password)) {
            throw new BusinessException(40000, "请输入密码");
        }
        if (authType == SshAuthType.KEY && isBlank(privateKey)) {
            throw new BusinessException(40000, "请输入私钥内容");
        }

        SshClient client = SshClient.setUpDefaultClient();
        client.start();
        try {
            ClientSession session = client
                    .connect(req.getUsername(), req.getHost(), port)
                    .verify(10000)
                    .getSession();
            try {
                if (authType == SshAuthType.PASSWORD) {
                    session.addPasswordIdentity(password);
                } else {
                    FilePasswordProvider pw = (sess, resource, attemptNo) -> null;
                    Iterable<KeyPair> keys = SecurityUtils.loadKeyPairIdentities(session, null,
                            new ByteArrayInputStream(privateKey.getBytes(StandardCharsets.UTF_8)), pw);
                    int count = 0;
                    if (keys != null) {
                        for (KeyPair kp : keys) { session.addPublicKeyIdentity(kp); count++; }
                    }
                    if (count == 0) throw new BusinessException(40002, "私钥无法解析，仅支持 OpenSSH 格式私钥");
                }
                // 走到这里 TCP 连接与密钥交换已成功，此阶段的失败基本都是认证被拒
                try {
                    session.auth().verify(10000);
                    if (!session.isAuthenticated()) {
                        throw new BusinessException(40002, "SSH 认证失败，请检查用户名与密码（或私钥）");
                    }
                } catch (BusinessException e) {
                    throw e;
                } catch (Exception authEx) {
                    throw new BusinessException(40002, "SSH 认证失败：服务器拒绝了登录凭据，请检查用户名与密码（或私钥）（网络与端口可达）");
                }
            } finally {
                session.close(true);
            }
            return "连接成功：" + req.getUsername() + "@" + req.getHost() + ":" + port;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(40002, classifySshError(e));
        } finally {
            try { client.stop(); } catch (Exception ignored) { }
        }
    }

    /** 把 sshd 底层异常映射成用户能看懂的原因（遍历整条异常链，避免最底层 cause 丢失关键语义） */
    private String classifySshError(Exception e) {
        StringBuilder chain = new StringBuilder();
        boolean authFailure = false;
        Throwable cur = e;
        java.util.Set<Throwable> seen = new java.util.HashSet<>();
        while (cur != null && seen.add(cur)) {
            if (cur.getClass().getSimpleName().toLowerCase().contains("auth")) authFailure = true;
            if (cur.getMessage() != null) chain.append(cur.getMessage().toLowerCase()).append(' ');
            cur = cur.getCause();
        }
        String lower = chain.toString();
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (lower.contains("refused") || lower.contains("unknown host") || lower.contains("no route")
                || lower.contains("unreachable") || lower.contains("network")) {
            return "无法连接到目标主机：" + detail;
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "连接超时，请确认主机地址与端口可达";
        }
        if (authFailure || lower.contains("auth fail") || lower.contains("denied")
                || lower.contains("publickey") || lower.contains("none supported")) {
            return "SSH 认证失败，请检查用户名与密码（或私钥）";
        }
        return "连接失败：" + detail;
    }

    private void validate(SshConfig config, boolean requireSecret) {
        // nodeId 可为空：空表示不依附于 Swarm 节点的自定义主机
        if (isBlank(config.getHost())) {
            throw new BusinessException(40000, "主机地址不能为空");
        }
        if (isBlank(config.getUsername())) {
            throw new BusinessException(40000, "登录用户名不能为空");
        }
        if (isBlank(config.getName()) && isBlank(config.getNodeId())) {
            config.setName(config.getHost());
        }
        if (config.getAuthType() == null) {
            config.setAuthType(SshAuthType.PASSWORD);
        }
        if (config.getPort() == null || config.getPort() <= 0 || config.getPort() > 65535) {
            config.setPort(22);
        }
        if (config.getAuthType() == SshAuthType.PASSWORD
                && requireSecret && isBlank(config.getPassword())) {
            throw new BusinessException(40000, "密码不能为空");
        }
        if (config.getAuthType() == SshAuthType.KEY
                && requireSecret && isBlank(config.getPrivateKey())) {
            throw new BusinessException(40000, "私钥内容不能为空");
        }
    }

    /**
     * 抹掉凭据字段，避免通过接口泄露
     */
    private SshConfig maskSecret(SshConfig config) {
        config.setPassword(null);
        config.setPrivateKey(null);
        return config;
    }

    /** 就地解密凭据，供建立 SSH 连接使用（实体在无事务的只读查询中返回，不会被脏检查回写） */
    private void decryptSecrets(SshConfig config) {
        config.setPassword(secretCipher.decrypt(config.getPassword()));
        config.setPrivateKey(secretCipher.decrypt(config.getPrivateKey()));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private boolean isNotBlank(String s) {
        return !isBlank(s);
    }
}
