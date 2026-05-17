package com.xianhua.papercheck.service;

import com.xianhua.papercheck.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path localRoot;

    public FileStorageService(@Value("${app.storage.local-root}") String localRoot) {
        this.localRoot = Path.of(localRoot);
    }

    public StoredFile save(MultipartFile file, Long userId) {
        try {
            if (file == null || file.isEmpty()) {
                throw new BusinessException("上传文件不能为空");
            }
            String originalName = file.getOriginalFilename() == null ? "paper" : file.getOriginalFilename();
            String suffix = "";
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex >= 0) {
                suffix = originalName.substring(dotIndex);
            }
            Path directory = localRoot.resolve(String.valueOf(userId)).resolve(LocalDate.now().toString());
            Files.createDirectories(directory);
            Path target = directory.resolve(UUID.randomUUID() + suffix);
            byte[] bytes = file.getBytes();
            Files.write(target, bytes);
            return new StoredFile(target.toString(), md5(bytes));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("文件保存失败");
        }
    }

    private String md5(byte[] bytes) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(bytes);
        StringBuilder builder = new StringBuilder();
        for (byte item : digest) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }

    public record StoredFile(String storagePath, String fileHash) {
    }
}
