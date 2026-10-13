package com.dockerportal.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtTokenProvider（登录令牌签发/校验）纯逻辑单测，不依赖 Spring 上下文。
 * HS256 要求密钥 ≥ 256 bit，故 secret 用 32+ 字节字符串。
 */
class JwtTokenProviderTest {

    private static final String SECRET =
            "unit-test-secret-key-please-use-at-least-32-bytes-abcdefg";
    private static final String OTHER_SECRET =
            "a-completely-different-secret-key-also-long-enough-99999";
    private static final long ACCESS_TTL = 60_000L;
    private static final long REFRESH_TTL = 3_600_000L;

    private JwtTokenProvider provider(String secret, long accessTtl) {
        return new JwtTokenProvider(secret, accessTtl, REFRESH_TTL);
    }

    // L2-J1 签发/校验往返
    @Test
    void generateThenValidate() {
        JwtTokenProvider p = provider(SECRET, ACCESS_TTL);
        String token = p.generateAccessToken("alice", "ADMIN");
        assertThat(p.validateToken(token)).isTrue();
    }

    // L2-J2 声明还原
    @Test
    void claimsRoundTrip() {
        JwtTokenProvider p = provider(SECRET, ACCESS_TTL);
        String token = p.generateAccessToken("bob", "OPERATOR", true);
        assertThat(p.getUsernameFromToken(token)).isEqualTo("bob");
        assertThat(p.getRoleFromToken(token)).isEqualTo("OPERATOR");
        assertThat(p.getMustChangeFromToken(token)).isTrue();

        String normal = p.generateAccessToken("bob", "OPERATOR");
        assertThat(p.getMustChangeFromToken(normal)).isFalse();
    }

    // L2-J3 过期令牌失效（用负 TTL 造出已过期令牌，免 sleep）
    @Test
    void expiredTokenIsRejected() {
        JwtTokenProvider p = provider(SECRET, -1_000L);
        String token = p.generateAccessToken("carol", "ADMIN");
        assertThat(p.validateToken(token)).isFalse();
    }

    // L2-J4 异密钥签发的令牌被拒
    @Test
    void tokenSignedWithOtherKeyIsRejected() {
        String foreign = provider(OTHER_SECRET, ACCESS_TTL).generateAccessToken("dave", "ADMIN");
        assertThat(provider(SECRET, ACCESS_TTL).validateToken(foreign)).isFalse();
    }

    // L2-J5 垃圾/被篡改字符串被拒，且不抛异常
    @Test
    void garbageTokenIsRejectedWithoutThrowing() {
        JwtTokenProvider p = provider(SECRET, ACCESS_TTL);
        assertThat(p.validateToken("not-a-jwt")).isFalse();
        assertThat(p.validateToken("")).isFalse();
        assertThat(p.validateToken(null)).isFalse();

        String valid = p.generateAccessToken("erin", "ADMIN");
        String tampered = valid.substring(0, valid.length() - 2) + "xx";
        assertThat(p.validateToken(tampered)).isFalse();
    }

    // refreshToken 与 accessToken 都是本密钥可校验的合法令牌
    @Test
    void refreshTokenAlsoValid() {
        JwtTokenProvider p = provider(SECRET, ACCESS_TTL);
        String refresh = p.generateRefreshToken("frank", "ADMIN");
        assertThat(p.validateToken(refresh)).isTrue();
        assertThat(p.getUsernameFromToken(refresh)).isEqualTo("frank");
    }
}
