package com.neuroequilibrium.nevertigo_qa.logging;

import java.time.LocalDateTime;

public class QuestionLog {

    private String question;
    private String metric;
    private double confidence;
    private long responseTimeMs;
    private String outcome;
    private LocalDateTime timestamp;

    public QuestionLog() {
        this.timestamp = LocalDateTime.now();
    }

    public QuestionLog(
            String question,
            String metric,
            double confidence,
            long responseTimeMs,
            String outcome) {

        this.question = question;
        this.metric = metric;
        this.confidence = confidence;
        this.responseTimeMs = responseTimeMs;
        this.outcome = outcome;
        this.timestamp = LocalDateTime.now();
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}