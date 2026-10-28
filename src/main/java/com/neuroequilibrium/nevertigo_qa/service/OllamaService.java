
package com.neuroequilibrium.nevertigo_qa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class OllamaService {

    private static final Logger logger =
            LoggerFactory.getLogger(OllamaService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String OLLAMA_URL =
            "http://localhost:11434/api/generate";

    public String ask(String prompt) {

        long startTime = System.nanoTime();

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", "qwen2.5:7b",
                    "prompt", prompt,
                    "stream", false
            );

            Map<String, Object> response =
                    restTemplate.postForObject(
                            OLLAMA_URL,
                            requestBody,
                            Map.class
                    );

            double elapsedSeconds =
                    (System.nanoTime() - startTime) / 1_000_000_000.0;

            logger.info(
                    "Ollama response time: {} seconds",
                    String.format("%.3f", elapsedSeconds)
            );

            if (response != null && response.containsKey("response")) {
                return response.get("response").toString();
            }

            logger.warn("Ollama returned an empty response.");
            return "";

        } catch (Exception e) {
            double elapsedSeconds =
                    (System.nanoTime() - startTime) / 1_000_000_000.0;

            logger.error(
                    "Ollama request failed after {} seconds: {}",
                    String.format("%.3f", elapsedSeconds),
                    e.getMessage()
            );

            throw e;
        }
    }
}
