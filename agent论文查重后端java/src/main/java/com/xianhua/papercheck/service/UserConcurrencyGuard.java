package com.xianhua.papercheck.service;

import com.xianhua.papercheck.common.BusinessException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class UserConcurrencyGuard {
    private static final int MAX_PROCESSING_PER_USER = 3;
    private final ConcurrentHashMap<Long, AtomicInteger> counters = new ConcurrentHashMap<>();

    public void enter(Long userId) {
        AtomicInteger counter = counters.computeIfAbsent(userId, key -> new AtomicInteger());
        int current = counter.incrementAndGet();
        if (current > MAX_PROCESSING_PER_USER) {
            counter.decrementAndGet();
            throw new BusinessException("当前用户同时处理的论文任务过多，请稍后再试");
        }
    }

    public void leave(Long userId) {
        AtomicInteger counter = counters.get(userId);
        if (counter == null) {
            return;
        }
        if (counter.decrementAndGet() <= 0) {
            counters.remove(userId);
        }
    }
}
