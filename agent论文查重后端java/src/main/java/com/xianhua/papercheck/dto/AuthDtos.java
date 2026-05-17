package com.xianhua.papercheck.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {
    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 32) String username,
            @NotBlank @Size(min = 6, max = 64) String password,
            String nickname
    ) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record UserInfo(Long id, String username, String nickname, String role) {
    }

    public record LoginResponse(String token, long expiresInSeconds, UserInfo user) {
    }
}
