package com.xianhua.papercheck.controller;

import com.xianhua.papercheck.common.ApiResponse;
import com.xianhua.papercheck.dto.AssistantDtos;
import com.xianhua.papercheck.security.CurrentUser;
import com.xianhua.papercheck.service.AssistantChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/assistant")
public class AssistantChatController {
    private final AssistantChatService assistantChatService;

    public AssistantChatController(AssistantChatService assistantChatService) {
        this.assistantChatService = assistantChatService;
    }

    @GetMapping("/sessions")
    public ApiResponse<List<AssistantDtos.ChatSessionItem>> sessions() {
        return ApiResponse.ok(assistantChatService.sessions(CurrentUser.id()));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<AssistantDtos.ChatMessageItem>> messages(@PathVariable Long sessionId) {
        return ApiResponse.ok(assistantChatService.messages(CurrentUser.id(), sessionId));
    }

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AssistantDtos.ChatResponse> chat(
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "image", required = false) MultipartFile image
    ) {
        return ApiResponse.ok(assistantChatService.chat(CurrentUser.id(), sessionId, message, image));
    }
}
