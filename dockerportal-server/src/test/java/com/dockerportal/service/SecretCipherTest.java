package com.dockerportal.service;

import com.dockerportal.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SecretCipher（SSH/镜像仓库凭据的 AES-256-GCM 落库加密）纯逻辑单测。
 * 不依赖 Spring 上下文与 Docker，用 @TempDir 提供独立数据目录。
 */
class SecretCipherTest {

    private SecretCipher cipherAt(Path dataPath) {
        SecretCipher c = new SecretCipher(dataPath.toString());
        c.init(); // @PostConstruct，包级可见，测试同包可直接调
        return c;
    }

    // L2-S1 加密解密往返
    @Test
    void encryptThenDecryptRoundTrip(@TempDir Path dir) {
        SecretCipher c = cipherAt(dir);
        String plain = "P@ssw0rd-中文-与符号!#%";
        String enc = c.encrypt(plain);

        assertThat(enc).startsWith("enc:v1:").isNotEqualTo(plain);
        assertThat(c.decrypt(enc)).isEqualTo(plain);
    }

    // L2-S2 加密幂等：已加密不再二次包裹；空/空白原样返回
    @Test
    void encryptIsIdempotentAndPassesThroughBlank(@TempDir Path dir) {
        SecretCipher c = cipherAt(dir);
        String once = c.encrypt("secret");
        assertThat(c.encrypt(once)).isEqualTo(once);
        assertThat(c.encrypt(null)).isNull();
        assertThat(c.encrypt("   ")).isEqualTo("   ");
    }

    // L2-S3 历史明文（无 enc: 前缀）透明读取
    @Test
    void decryptPassesThroughLegacyPlaintext(@TempDir Path dir) {
        SecretCipher c = cipherAt(dir);
        assertThat(c.decrypt("legacy-plain")).isEqualTo("legacy-plain");
        assertThat(c.isEncrypted("legacy-plain")).isFalse();
        assertThat(c.isEncrypted(c.encrypt("x"))).isTrue();
    }

    // L2-S4 篡改检测：密文被改动后 GCM 认证失败应抛业务异常
    @Test
    void decryptRejectsTamperedCiphertext(@TempDir Path dir) {
        SecretCipher c = cipherAt(dir);
        String enc = c.encrypt("do-not-tamper");
        // 破坏 Base64 载荷最后一个有效字符，保持前缀合法以进入解密分支
        String tampered = enc.substring(0, enc.length() - 2) + (enc.endsWith("A") ? "B" : "A") + enc.substring(enc.length() - 1);

        assertThatThrownBy(() -> c.decrypt(tampered))
                .isInstanceOf(BusinessException.class);
    }

    // L2-S4b 换密钥后旧密文解不开
    @Test
    void decryptFailsWithDifferentKey(@TempDir Path dirA, @TempDir Path dirB) {
        String enc = cipherAt(dirA).encrypt("cross-key-secret");
        SecretCipher other = cipherAt(dirB); // 另一目录 → 另一把随机密钥
        assertThatThrownBy(() -> other.decrypt(enc)).isInstanceOf(BusinessException.class);
    }

    // L2-S5 密钥持久化：同目录重建实例应读回同一把密钥，能解旧密文
    @Test
    void keyIsPersistedAcrossInstances(@TempDir Path dir) {
        String enc = cipherAt(dir).encrypt("persist-me");
        SecretCipher reloaded = cipherAt(dir);
        assertThat(reloaded.decrypt(enc)).isEqualTo("persist-me");
    }

    // 同一明文两次加密密文不同（GCM 随机 IV），但都能解回
    @Test
    void encryptUsesRandomIvEachCall(@TempDir Path dir) {
        SecretCipher c = cipherAt(dir);
        String a = c.encrypt("same-input");
        String b = c.encrypt("same-input");
        assertThat(a).isNotEqualTo(b);
        assertThat(c.decrypt(a)).isEqualTo(c.decrypt(b)).isEqualTo("same-input");
    }
}
