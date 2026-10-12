package com.dockerportal.service;

import com.dockerportal.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感凭据（SSH 密码/私钥）落库前的对称加密。
 * AES-256-GCM，密钥存放在数据目录下的 .secret.key（与 H2 数据库文件同目录），
 * 首次启动自动生成。密文带 enc:v1: 前缀，无前缀的历史明文可透明读取并在迁移后补加密。
 */
@Slf4j
@Component
public class SecretCipher {

    private static final String PREFIX = "enc:v1:";
    private static final String KEY_FILE = ".secret.key";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String dataPath;
    private final SecureRandom random = new SecureRandom();
    private SecretKey key;

    public SecretCipher(@Value("${dockerportal.data-path:./data}") String dataPath) {
        this.dataPath = dataPath;
    }

    @PostConstruct
    void init() {
        try {
            Path keyFile = Paths.get(dataPath, KEY_FILE);
            if (Files.exists(keyFile)) {
                byte[] decoded = Base64.getDecoder().decode(Files.readString(keyFile).trim());
                key = new SecretKeySpec(decoded, "AES");
                log.info("SSH 凭据密钥已加载: {}", keyFile.toAbsolutePath());
            } else {
                KeyGenerator generator = KeyGenerator.getInstance("AES");
                generator.init(256, random);
                key = generator.generateKey();
                Files.createDirectories(keyFile.getParent());
                Files.writeString(keyFile, Base64.getEncoder().encodeToString(key.getEncoded()));
                try {
                    Files.setPosixFilePermissions(keyFile,
                            java.util.EnumSet.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
                } catch (UnsupportedOperationException | IOException ignored) {
                    // 非 POSIX 文件系统尽力而为
                }
                log.info("SSH 凭据密钥已生成: {}", keyFile.toAbsolutePath());
            }
        } catch (Exception e) {
            throw new IllegalStateException("初始化 SSH 凭据加密密钥失败", e);
        }
    }

    /** 加密；空值与已加密内容原样返回（幂等，可安全重复调用） */
    public String encrypt(String plain) {
        if (plain == null || plain.isBlank() || plain.startsWith(PREFIX)) {
            return plain;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new BusinessException(50000, "凭据加密失败");
        }
    }

    /** 解密；无前缀视为历史明文直接返回 */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_BITS, payload, 0, GCM_IV_LENGTH));
            byte[] plain = cipher.doFinal(payload, GCM_IV_LENGTH, payload.length - GCM_IV_LENGTH);
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("SSH 凭据解密失败，可能是数据目录下的 {} 已被更换", KEY_FILE);
            throw new BusinessException(40002, "凭据解密失败，请重新保存该 SSH 配置的密码/私钥");
        }
    }

    /** 判断是否为本组件产出的密文 */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
