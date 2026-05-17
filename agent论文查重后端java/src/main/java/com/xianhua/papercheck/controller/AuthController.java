package com.xianhua.papercheck.controller;

import com.xianhua.papercheck.common.ApiResponse;
import com.xianhua.papercheck.dto.AuthDtos;
import com.xianhua.papercheck.security.CurrentUser;
import com.xianhua.papercheck.security.LoginUser;
import com.xianhua.papercheck.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthDtos.LoginResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthDtos.UserInfo> me() {
        LoginUser user = CurrentUser.get();
        return ApiResponse.ok(new AuthDtos.UserInfo(user.getUserId(), user.getUsername(), user.getNickname(), user.getRole()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            authService.logout(authorization.substring(7));
        }
        return ApiResponse.ok(null);
    }
}
