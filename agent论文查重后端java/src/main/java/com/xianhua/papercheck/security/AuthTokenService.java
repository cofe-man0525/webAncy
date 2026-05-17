package com.xianhua.papercheck.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthTokenService {
    private static final String TOKEN_KEY_PREFIX = "papercheck:auth:token:";
    private static final String USER_TOKEN_KEY_PREFIX = "papercheck:auth:user:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public AuthTokenService(
            StringRedisTemplate redisTemplate,
            @Value("${app.jwt.expire-minutes}") long expireMinutes
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMinutes(expireMinutes);
    }

    public void store(Long userId, String username, String token) {
        String value = buildValue(userId, username);
        redisTemplate.opsForValue().set(tokenKey(token), value, ttl);
        redisTemplate.opsForSet().add(userTokensKey(userId), token);
        redisTemplate.expire(userTokensKey(userId), ttl);
    }

    public boolean isValid(Long userId, String username, String token) {
        String stored = redisTemplate.opsForValue().get(tokenKey(token));
        return buildValue(userId, username).equals(stored);
    }

    public void remove(Long userId, String token) {
        redisTemplate.delete(tokenKey(token));
        redisTemplate.opsForSet().remove(userTokensKey(userId), token);
    }

    public long expiresInSeconds() {
        return ttl.toSeconds();
    }

    private String tokenKey(String token) {
        return TOKEN_KEY_PREFIX + token;
    }

    private String userTokensKey(Long userId) {
        return USER_TOKEN_KEY_PREFIX + userId + ":tokens";
    }

    private String buildValue(Long userId, String username) {
        return userId + ":" + username;
    }
}
