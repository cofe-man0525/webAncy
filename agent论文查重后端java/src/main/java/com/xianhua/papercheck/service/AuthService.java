package com.xianhua.papercheck.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianhua.papercheck.common.BusinessException;
import com.xianhua.papercheck.dto.AuthDtos;
import com.xianhua.papercheck.entity.User;
import com.xianhua.papercheck.entity.UserSetting;
import com.xianhua.papercheck.mapper.UserMapper;
import com.xianhua.papercheck.mapper.UserSettingMapper;
import com.xianhua.papercheck.security.AuthTokenService;
import com.xianhua.papercheck.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final UserSettingMapper userSettingMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthTokenService authTokenService;

    public AuthService(
            UserMapper userMapper,
            UserSettingMapper userSettingMapper,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AuthTokenService authTokenService
    ) {
        this.userMapper = userMapper;
        this.userSettingMapper = userSettingMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.authTokenService = authTokenService;
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthDtos.LoginResponse register(AuthDtos.RegisterRequest request) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null || request.nickname().isBlank() ? request.username() : request.nickname());
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setDeleted(0);
        userMapper.insert(user);

        UserSetting setting = new UserSetting();
        setting.setUserId(user.getId());
        setting.setDefaultStyle("academic");
        setting.setEnableRag(true);
        setting.setHighRiskThreshold(75);
        setting.setSuggestionCount(2);
        setting.setLlmBaseUrl("");
        setting.setLlmModel("");
        setting.setLlmApiKey("");
        setting.setCreatedAt(now);
        setting.setUpdatedAt(now);
        userSettingMapper.insert(setting);

        return buildLoginResponse(user);
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        return buildLoginResponse(user);
    }

    public void logout(String token) {
        Long userId = jwtService.parseUserId(token);
        if (userId != null) {
            authTokenService.remove(userId, token);
        }
    }

    private AuthDtos.LoginResponse buildLoginResponse(User user) {
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        authTokenService.store(user.getId(), user.getUsername(), token);
        AuthDtos.UserInfo userInfo = new AuthDtos.UserInfo(user.getId(), user.getUsername(), user.getNickname(), user.getRole());
        return new AuthDtos.LoginResponse(token, authTokenService.expiresInSeconds(), userInfo);
    }
}
