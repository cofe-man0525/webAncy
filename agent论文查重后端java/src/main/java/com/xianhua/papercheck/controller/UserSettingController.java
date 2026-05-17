package com.xianhua.papercheck.controller;

import com.xianhua.papercheck.common.ApiResponse;
import com.xianhua.papercheck.dto.UserSettingDtos;
import com.xianhua.papercheck.security.CurrentUser;
import com.xianhua.papercheck.service.UserSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/settings")
public class UserSettingController {
    private final UserSettingService userSettingService;

    public UserSettingController(UserSettingService userSettingService) {
        this.userSettingService = userSettingService;
    }

    @GetMapping
    public ApiResponse<UserSettingDtos.SettingResponse> get() {
        return ApiResponse.ok(userSettingService.getSetting(CurrentUser.id()));
    }

    @PutMapping
    public ApiResponse<UserSettingDtos.SettingResponse> update(@RequestBody UserSettingDtos.UpdateSettingRequest request) {
        return ApiResponse.ok(userSettingService.updateSetting(CurrentUser.id(), request));
    }
}
