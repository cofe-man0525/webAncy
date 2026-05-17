package com.xianhua.papercheck.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianhua.papercheck.dto.UserSettingDtos;
import com.xianhua.papercheck.entity.UserSetting;
import com.xianhua.papercheck.mapper.UserSettingMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserSettingService {
    private final UserSettingMapper userSettingMapper;

    public UserSettingService(UserSettingMapper userSettingMapper) {
        this.userSettingMapper = userSettingMapper;
    }

    public UserSettingDtos.SettingResponse getSetting(Long userId) {
        UserSetting setting = findOrCreate(userId);
        return new UserSettingDtos.SettingResponse(
                setting.getDefaultStyle(),
                setting.getEnableRag(),
                setting.getHighRiskThreshold(),
                setting.getSuggestionCount(),
                setting.getLlmBaseUrl(),
                setting.getLlmModel(),
                setting.getLlmApiKey() != null && !setting.getLlmApiKey().isBlank()
        );
    }

    public UserSetting getSettingEntity(Long userId) {
        return findOrCreate(userId);
    }

    public UserSettingDtos.SettingResponse updateSetting(Long userId, UserSettingDtos.UpdateSettingRequest request) {
        UserSetting setting = findOrCreate(userId);
        if (request.defaultStyle() != null) {
            setting.setDefaultStyle(request.defaultStyle());
        }
        if (request.enableRag() != null) {
            setting.setEnableRag(request.enableRag());
        }
        if (request.highRiskThreshold() != null) {
            setting.setHighRiskThreshold(request.highRiskThreshold());
        }
        if (request.suggestionCount() != null) {
            setting.setSuggestionCount(request.suggestionCount());
        }
        if (request.llmBaseUrl() != null) {
            setting.setLlmBaseUrl(blankToNull(request.llmBaseUrl()));
        }
        if (request.llmModel() != null) {
            setting.setLlmModel(blankToNull(request.llmModel()));
        }
        if (Boolean.TRUE.equals(request.clearLlmApiKey())) {
            setting.setLlmApiKey(null);
        } else if (request.llmApiKey() != null && !request.llmApiKey().isBlank()) {
            setting.setLlmApiKey(request.llmApiKey().trim());
        }
        setting.setUpdatedAt(LocalDateTime.now());
        userSettingMapper.updateById(setting);
        return getSetting(userId);
    }

    private UserSetting findOrCreate(Long userId) {
        UserSetting setting = userSettingMapper.selectOne(new LambdaQueryWrapper<UserSetting>().eq(UserSetting::getUserId, userId));
        if (setting != null) {
            return setting;
        }

        LocalDateTime now = LocalDateTime.now();
        setting = new UserSetting();
        setting.setUserId(userId);
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
        return setting;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
