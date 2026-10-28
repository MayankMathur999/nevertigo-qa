package com.neuroequilibrium.nevertigo_qa.logging;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class QuestionLogService {

    private final List<QuestionLog> logs =
            Collections.synchronizedList(new ArrayList<>());

    public void saveLog(QuestionLog log) {
        logs.add(log);
    }

    public List<QuestionLog> getAllLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    public List<QuestionLog> getLowConfidenceLogs() {
        synchronized (logs) {
            return logs.stream()
                    .filter(log -> log.getConfidence() < 0.50)
                    .toList();
        }
    }
}