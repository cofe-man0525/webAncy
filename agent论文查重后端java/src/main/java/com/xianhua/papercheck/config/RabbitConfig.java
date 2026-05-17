package com.xianhua.papercheck.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean
    public DirectExchange analysisExchange(@Value("${app.rabbit.exchange}") String exchange) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public Queue analysisQueue() {
        return new Queue("papercheck.analysis.queue", true);
    }

    @Bean
    public Binding analysisBinding(
            DirectExchange analysisExchange,
            Queue analysisQueue,
            @Value("${app.rabbit.routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(analysisQueue).to(analysisExchange).with(routingKey);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
