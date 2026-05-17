package com.xianhua.papercheck.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianhua.papercheck.common.BusinessException;
import com.xianhua.papercheck.dto.AnalysisDtos;
import com.xianhua.papercheck.entity.AnalysisTask;
import com.xianhua.papercheck.entity.Paper;
import com.xianhua.papercheck.entity.UserSetting;
import com.xianhua.papercheck.mapper.AnalysisTaskMapper;
import com.xianhua.papercheck.mapper.PaperMapper;
import com.xianhua.papercheck.mq.AnalysisTaskMessage;
import com.xianhua.papercheck.mq.AnalysisTaskPublisher;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class PaperService {
    private final PaperMapper paperMapper;
    private final AnalysisTaskMapper analysisTaskMapper;
    private final FileStorageService fileStorageService;
    private final AnalysisTaskPublisher analysisTaskPublisher;
    private final RedissonClient redissonClient;
    private final UserSettingService userSettingService;

    public PaperService(
            PaperMapper paperMapper,
            AnalysisTaskMapper analysisTaskMapper,
            FileStorageService fileStorageService,
            AnalysisTaskPublisher analysisTaskPublisher,
            RedissonClient redissonClient,
            UserSettingService userSettingService
    ) {
        this.paperMapper = paperMapper;
        this.analysisTaskMapper = analysisTaskMapper;
        this.fileStorageService = fileStorageService;
        this.analysisTaskPublisher = analysisTaskPublisher;
        this.redissonClient = redissonClient;
        this.userSettingService = userSettingService;
    }

    @Transactional(rollbackFor = Exception.class)
    public AnalysisDtos.UploadResponse upload(
            Long userId,
            MultipartFile file,
            String depth,
            String style,
            Boolean enableRag,
            Integer suggestionCount
    ) {
        RLock lock = redissonClient.getLock("papercheck:lock:user:" + userId + ":upload");
        boolean locked;
        try {
            locked = lock.tryLock(3, 30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("上传请求被中断，请稍后重试");
        }

        if (!locked) {
            throw new BusinessException("当前用户上传任务繁忙，请稍后重试");
        }

        registerUnlockAfterTransaction(lock);
        return createAnalysisTask(userId, file, depth, style, enableRag, suggestionCount);
    }

    private AnalysisDtos.UploadResponse createAnalysisTask(
            Long userId,
            MultipartFile file,
            String depth,
            String style,
            Boolean enableRag,
            Integer suggestionCount
    ) {
        Long activeCount = analysisTaskMapper.selectCount(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getUserId, userId)
                .in(AnalysisTask::getStatus, "queued", "processing"));
        if (activeCount >= 3) {
            throw new BusinessException("当前已有 3 个任务正在处理，请等待完成后再上传");
        }

        FileStorageService.StoredFile storedFile = fileStorageService.save(file, userId);
        LocalDateTime now = LocalDateTime.now();

        Paper paper = new Paper();
        paper.setUserId(userId);
        paper.setOriginalName(file.getOriginalFilename());
        paper.setContentType(file.getContentType());
        paper.setFileSize(file.getSize());
        paper.setStoragePath(storedFile.storagePath());
        paper.setFileHash(storedFile.fileHash());
        paper.setCreatedAt(now);
        paperMapper.insert(paper);

        AnalysisTask task = new AnalysisTask();
        task.setUserId(userId);
        task.setPaperId(paper.getId());
        task.setStatus("queued");
        task.setProgress(0);
        task.setDepth(depth == null ? "standard" : depth);
        task.setStyle(style == null ? "academic" : style);
        task.setEnableRag(enableRag == null || enableRag);
        task.setSuggestionCount(suggestionCount == null ? 2 : suggestionCount);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setVersion(0);
        analysisTaskMapper.insert(task);
        UserSetting setting = userSettingService.getSettingEntity(userId);

        AnalysisTaskMessage message = new AnalysisTaskMessage(
                task.getId(),
                userId,
                paper.getId(),
                paper.getStoragePath(),
                task.getDepth(),
                task.getStyle(),
                task.getEnableRag(),
                task.getSuggestionCount(),
                setting.getLlmBaseUrl(),
                setting.getLlmApiKey(),
                setting.getLlmModel()
        );
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                analysisTaskPublisher.publish(message);
            }
        });

        return new AnalysisDtos.UploadResponse(task.getId(), task.getStatus());
    }

    private void registerUnlockAfterTransaction(RLock lock) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        });
    }
}
