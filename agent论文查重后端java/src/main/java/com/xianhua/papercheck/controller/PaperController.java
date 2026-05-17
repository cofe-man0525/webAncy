package com.xianhua.papercheck.controller;

import com.xianhua.papercheck.common.ApiResponse;
import com.xianhua.papercheck.dto.AnalysisDtos;
import com.xianhua.papercheck.security.CurrentUser;
import com.xianhua.papercheck.service.PaperService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/papers")
public class PaperController {
    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    @PostMapping("/upload")
    public ApiResponse<AnalysisDtos.UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "standard") String depth,
            @RequestParam(defaultValue = "academic") String style,
            @RequestParam(defaultValue = "true") Boolean enableRag,
            @RequestParam(defaultValue = "2") Integer suggestionCount
    ) {
        return ApiResponse.ok(paperService.upload(CurrentUser.id(), file, depth, style, enableRag, suggestionCount));
    }
}
