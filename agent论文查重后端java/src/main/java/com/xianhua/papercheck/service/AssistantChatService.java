package com.xianhua.papercheck.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.xianhua.papercheck.common.BusinessException;
import com.xianhua.papercheck.dto.AssistantDtos;
import com.xianhua.papercheck.entity.UserSetting;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class AssistantChatService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final UserSettingService userSettingService;
    private final PythonAssistantClient pythonAssistantClient;

    public AssistantChatService(
            JdbcTemplate jdbcTemplate,
            UserSettingService userSettingService,
            PythonAssistantClient pythonAssistantClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.userSettingService = userSettingService;
        this.pythonAssistantClient = pythonAssistantClient;
    }

    @PostConstruct
    public void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_chat_sessions (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  title VARCHAR(100) NOT NULL,
                  created_at DATETIME NOT NULL,
                  updated_at DATETIME NOT NULL,
                  deleted TINYINT NOT NULL DEFAULT 0,
                  KEY idx_ai_chat_sessions_user_updated (user_id, updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='论文助手会话'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_chat_messages (
                  id BIGINT PRIMARY KEY,
                  session_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  role VARCHAR(20) NOT NULL,
                  content TEXT NOT NULL,
                  image_name VARCHAR(255),
                  image_content_type VARCHAR(100),
                  image_info VARCHAR(1000),
                  model VARCHAR(100),
                  created_at DATETIME NOT NULL,
                  KEY idx_ai_chat_messages_session_created (session_id, created_at),
                  KEY idx_ai_chat_messages_user_created (user_id, created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='论文助手消息'
                """);
    }

    public List<AssistantDtos.ChatSessionItem> sessions(Long userId) {
        return jdbcTemplate.query(
                """
                SELECT id, title, updated_at
                FROM ai_chat_sessions
                WHERE user_id = ? AND deleted = 0
                ORDER BY updated_at DESC
                LIMIT 50
                """,
                (rs, rowNum) -> new AssistantDtos.ChatSessionItem(
                        rs.getLong("id"),
                        rs.getString("title"),
                        format(rs.getTimestamp("updated_at"))
                ),
                userId
        );
    }

    public List<AssistantDtos.ChatMessageItem> messages(Long userId, Long sessionId) {
        ensureSessionOwner(userId, sessionId);
        return jdbcTemplate.query(
                """
                SELECT id, session_id, role, content, image_name, image_info, model, created_at
                FROM ai_chat_messages
                WHERE user_id = ? AND session_id = ?
                ORDER BY created_at ASC
                """,
                (rs, rowNum) -> new AssistantDtos.ChatMessageItem(
                        rs.getLong("id"),
                        rs.getLong("session_id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getString("image_name"),
                        rs.getString("image_info"),
                        rs.getString("model"),
                        format(rs.getTimestamp("created_at"))
                ),
                userId,
                sessionId
        );
    }

    public AssistantDtos.ChatResponse chat(Long userId, Long sessionId, String message, MultipartFile image) {
        String safeMessage = message == null ? "" : message.trim();
        if (safeMessage.isBlank() && (image == null || image.isEmpty())) {
            throw new BusinessException("请输入论文修改问题或上传图片");
        }

        Long realSessionId = sessionId == null ? createSession(userId, safeMessage, image) : sessionId;
        ensureSessionOwner(userId, realSessionId);
        List<Map<String, String>> history = recentHistory(userId, realSessionId);
        AssistantDtos.ChatMessageItem userMessage = saveMessage(
                userId,
                realSessionId,
                "user",
                safeMessage.isBlank() ? "请分析这张图片中的论文内容" : safeMessage,
                image == null ? null : image.getOriginalFilename(),
                image == null ? null : image.getContentType(),
                null,
                null
        );

        UserSetting setting = userSettingService.getSettingEntity(userId);
        PythonAssistantClient.AssistantPythonResponse response = pythonAssistantClient.chat(
                userId,
                realSessionId,
                userMessage.content(),
                image,
                setting,
                history
        );

        AssistantDtos.ChatMessageItem assistantMessage = saveMessage(
                userId,
                realSessionId,
                "assistant",
                response.answer(),
                null,
                null,
                response.imageInfo(),
                response.model()
        );
        touchSession(realSessionId, userMessage.content());
        return new AssistantDtos.ChatResponse(realSessionId, userMessage, assistantMessage, response.ragReferences());
    }

    private Long createSession(Long userId, String message, MultipartFile image) {
        Long sessionId = IdWorker.getId();
        LocalDateTime now = LocalDateTime.now();
        String title = buildTitle(message, image);
        jdbcTemplate.update(
                "INSERT INTO ai_chat_sessions (id, user_id, title, created_at, updated_at, deleted) VALUES (?, ?, ?, ?, ?, 0)",
                sessionId,
                userId,
                title,
                now,
                now
        );
        return sessionId;
    }

    private AssistantDtos.ChatMessageItem saveMessage(
            Long userId,
            Long sessionId,
            String role,
            String content,
            String imageName,
            String imageContentType,
            String imageInfo,
            String model
    ) {
        Long id = IdWorker.getId();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                INSERT INTO ai_chat_messages
                (id, session_id, user_id, role, content, image_name, image_content_type, image_info, model, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                sessionId,
                userId,
                role,
                content,
                imageName,
                imageContentType,
                imageInfo,
                model,
                now
        );
        return new AssistantDtos.ChatMessageItem(id, sessionId, role, content, imageName, imageInfo, model, FORMATTER.format(now));
    }

    private List<Map<String, String>> recentHistory(Long userId, Long sessionId) {
        return jdbcTemplate.queryForList(
                """
                SELECT role, content
                FROM (
                  SELECT role, content, created_at
                  FROM ai_chat_messages
                  WHERE user_id = ? AND session_id = ?
                  ORDER BY created_at DESC
                  LIMIT 8
                ) t
                ORDER BY created_at ASC
                """,
                userId,
                sessionId
        ).stream().map(item -> Map.of(
                "role", String.valueOf(item.get("role")),
                "content", String.valueOf(item.get("content"))
        )).toList();
    }

    private void ensureSessionOwner(Long userId, Long sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_chat_sessions WHERE id = ? AND user_id = ? AND deleted = 0",
                Integer.class,
                sessionId,
                userId
        );
        if (count == null || count == 0) {
            throw new BusinessException("会话不存在或无权访问");
        }
    }

    private void touchSession(Long sessionId, String message) {
        jdbcTemplate.update(
                "UPDATE ai_chat_sessions SET title = CASE WHEN title = '新的论文助手会话' THEN ? ELSE title END, updated_at = ? WHERE id = ?",
                buildTitle(message, null),
                LocalDateTime.now(),
                sessionId
        );
    }

    private String buildTitle(String message, MultipartFile image) {
        if (message != null && !message.isBlank()) {
            return message.length() > 28 ? message.substring(0, 28) + "..." : message;
        }
        if (image != null && !image.isEmpty()) {
            return "图片论文分析";
        }
        return "新的论文助手会话";
    }

    private static String format(Timestamp timestamp) {
        return timestamp == null ? "" : FORMATTER.format(timestamp.toLocalDateTime());
    }
}
