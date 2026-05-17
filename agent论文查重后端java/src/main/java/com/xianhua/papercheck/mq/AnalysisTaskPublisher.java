package com.xianhua.papercheck.mq;

import com.xianhua.papercheck.service.MockAnalysisService;
import com.xianhua.papercheck.service.PythonAgentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AnalysisTaskPublisher {
    private static final Logger log = LoggerFactory.getLogger(AnalysisTaskPublisher.class);

    private final boolean rabbitEnabled;
    private final String exchange;
    private final String routingKey;
    private final boolean aiServiceEnabled;
    private final boolean mockAnalysisEnabled;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final PythonAgentClient pythonAgentClient;
    private final MockAnalysisService mockAnalysisService;

    public AnalysisTaskPublisher(
            @Value("${app.rabbit.enabled:false}") boolean rabbitEnabled,
            @Value("${app.rabbit.exchange}") String exchange,
            @Value("${app.rabbit.routing-key}") String routingKey,
            @Value("${app.ai-service.enabled:false}") boolean aiServiceEnabled,
            @Value("${app.mock-analysis.enabled:false}") boolean mockAnalysisEnabled,
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
            PythonAgentClient pythonAgentClient,
            MockAnalysisService mockAnalysisService
    ) {
        this.rabbitEnabled = rabbitEnabled;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.aiServiceEnabled = aiServiceEnabled;
        this.mockAnalysisEnabled = mockAnalysisEnabled;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
        this.pythonAgentClient = pythonAgentClient;
        this.mockAnalysisService = mockAnalysisService;
    }

    public void publish(AnalysisTaskMessage message) {
        if (rabbitEnabled) {
            try {
                rabbitTemplateProvider.getObject().convertAndSend(exchange, routingKey, message);
                return;
            } catch (Exception exception) {
                if (!aiServiceEnabled) {
                    throw exception;
                }
                log.warn("RabbitMQ publish failed, fallback to Python AI HTTP. taskId={}", message.taskId(), exception);
            }
        }
        if (aiServiceEnabled) {
            pythonAgentClient.runTask(message.taskId());
            return;
        }
        if (mockAnalysisEnabled) {
            mockAnalysisService.runMockAnalysis(message.taskId());
            return;
        }
        throw new IllegalStateException("No real analysis backend configured. Enable app.ai-service or app.rabbit.");
    }
}
