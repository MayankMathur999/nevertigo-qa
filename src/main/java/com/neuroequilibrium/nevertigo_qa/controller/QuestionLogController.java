
package com.neuroequilibrium.nevertigo_qa.controller;

import com.neuroequilibrium.nevertigo_qa.logging.QuestionLog;
import com.neuroequilibrium.nevertigo_qa.logging.QuestionLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/logs")
public class QuestionLogController {

    private final QuestionLogService questionLogService;

    public QuestionLogController(QuestionLogService questionLogService) {
        this.questionLogService = questionLogService;
    }

    @GetMapping
    public List<QuestionLog> getAllLogs() {
        return questionLogService.getAllLogs();
    }

    @GetMapping("/low-confidence")
    public List<QuestionLog> getLowConfidenceLogs() {
        return questionLogService.getLowConfidenceLogs();
    }
}
