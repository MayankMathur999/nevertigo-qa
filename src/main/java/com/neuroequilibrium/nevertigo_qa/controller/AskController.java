
package com.neuroequilibrium.nevertigo_qa.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroequilibrium.nevertigo_qa.logging.QuestionLog;
import com.neuroequilibrium.nevertigo_qa.logging.QuestionLogService;
import com.neuroequilibrium.nevertigo_qa.service.OllamaService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/ask")
public class AskController {

    private final MetricsController metricsController;
    private final OllamaService ollamaService;
    private final CacheManager cacheManager;
    private final QuestionLogService questionLogService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CLASSIFIER_URL =
            "http://127.0.0.1:5000/predict";

    private static final double HIGH_CONFIDENCE = 0.75;
    private static final double MEDIUM_CONFIDENCE = 0.50;

    private static final String DEFAULT_START = "2026-05-01";
    private static final String DEFAULT_END = "2026-05-31";

    private static final String CACHE_NAME = "metricResults";

    // Retain confidence between the initial question and confirmation.
    private final Map<String, Double> pendingConfidences =
            new ConcurrentHashMap<>();

    private static final Set<String> SUPPORTED_METRICS = Set.of(
            "visits",
            "patients_with_visit",
            "new_patients",
            "followup_patients",
            "bppv_patients",
            "bppv_percentage",
            "net_neuro_revenue",
            "revenue_by_scheme",
            "revenue_by_staff",
            "analysis_amount",
            "dva_report_received",
            "medicine_line",
            "low_spv_no_torsion_visits",
            "visits_by_clinic",
            "revenue_by_clinic",
            "revenue_by_service",
            "clinic_count",
            "mumbai_dva_versions",
            "omar_may_details"
    );

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("january", 1),
            Map.entry("february", 2),
            Map.entry("march", 3),
            Map.entry("april", 4),
            Map.entry("may", 5),
            Map.entry("june", 6),
            Map.entry("july", 7),
            Map.entry("august", 8),
            Map.entry("september", 9),
            Map.entry("october", 10),
            Map.entry("november", 11),
            Map.entry("december", 12)
    );

    public AskController(
            MetricsController metricsController,
            OllamaService ollamaService,
            CacheManager cacheManager,
            QuestionLogService questionLogService) {

        this.metricsController = metricsController;
        this.ollamaService = ollamaService;
        this.cacheManager = cacheManager;
        this.questionLogService = questionLogService;
    }

    @PostMapping
    public Map<String, Object> ask(
            @RequestBody(required = false) Map<String, String> body) {

        Map<String, String> safeBody = body == null ? Map.of() : body;
        String submittedText = safeBody.get("question");

        if (submittedText == null || submittedText.isBlank()) {
            return Map.of("answer", "Please enter a question.");
        }

        // Keep confirmation requests as a single question; they contain extra fields.
        if ("true".equalsIgnoreCase(safeBody.get("confirmed"))) {
            return processAndLog(safeBody);
        }

        java.util.List<String> questions = splitQuestions(submittedText);
        System.out.println("Split questions: " + questions);

        // Preserve the original response format for ordinary single-question requests.
        if (questions.size() == 1) {
            Map<String, String> singleBody = new LinkedHashMap<>(safeBody);
            singleBody.put("question", questions.get(0));
            return processAndLog(singleBody);
        }

        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (String individualQuestion : questions) {
            Map<String, String> individualBody = new LinkedHashMap<>(safeBody);
            individualBody.put("question", individualQuestion);
            // A confirmation applies only to the exact question being confirmed.
            individualBody.remove("confirmed");
            individualBody.remove("metric");
            individualBody.remove("confidence");
            results.add(processAndLog(individualBody));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "multiple_answers");
        response.put("question", submittedText);
        response.put("count", results.size());
        response.put("results", results);
        return response;
    }

    /**
     * Splits a message containing multiple questions while leaving the existing
     * single-question flow unchanged. Supports question marks, separate lines,
     * and numbered questions such as "1. ... 2. ...".
     */
   


private java.util.List<String> splitQuestions(String text) {
    String normalized = text.trim()
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            // Split numbered questions on the same line.
            .replaceAll("\\s+(?=\\d+[.)]\\s+)", "\n")
            // Split questions joined by comma and/or "and".
            .replaceAll(
                    "(?i),?\\s+and\\s+(?=(how much|how many|what|which|who|when|where)\\b)",
                    "\n"
            );

    java.util.List<String> questions = new java.util.ArrayList<>();

    for (String line : normalized.split("\\n")) {
        String cleanedLine = line.trim()
                .replaceAll("^\\d+[.)]\\s*", "");

        if (cleanedLine.isBlank()) {
            continue;
        }

        // First, split at question marks.
        String[] parts = cleanedLine.split("(?<=\\?)\\s+(?=[A-Za-z0-9])");

        for (String part : parts) {
            String cleaned = part.trim()
                    .replaceAll("^[-*]\\s*", "")
                    .replaceAll("^\\d+[.)]\\s*", "");

            if (cleaned.isBlank()) {
                continue;
            }

            // Split a second question beginning with common question phrases.
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "(?i)\\s{2,}(?=(?:how many|how much|what|which|who|when|where)\\b)"
            ).matcher(cleaned);

            int lastStart = 0;
            java.util.List<String> splitParts = new java.util.ArrayList<>();

            while (matcher.find()) {
                splitParts.add(cleaned.substring(lastStart, matcher.start()).trim());
                lastStart = matcher.end();
            }

            splitParts.add(cleaned.substring(lastStart).trim());

            for (String splitPart : splitParts) {
                if (!splitPart.isBlank()) {
                    questions.add(splitPart);
                }
            }
        }
    }

    return questions;
}


    private Map<String, Object> processAndLog(Map<String, String> body) {
        long startedAt = System.nanoTime();
        String question = body.get("question");
        Map<String, Object> response;

        try {
            response = processQuestion(body);
        } catch (Exception e) {
            response = new LinkedHashMap<>();
            response.put("question", question == null ? "" : question);
            response.put("status", "error");
            response.put(
                    "answer",
                    "An unexpected error occurred while processing your question."
            );
            response.put(
                    "detail",
                    e.getMessage() == null ? "Unknown error" : e.getMessage()
            );
        }

        long responseTimeMs = (System.nanoTime() - startedAt) / 1_000_000;

        if (question != null && !question.isBlank()) {
            try {
                String metric = String.valueOf(response.getOrDefault("metric", "NONE"));
                Object confidenceValue = response.get("confidence");
                if (!(confidenceValue instanceof Number)) {
                    confidenceValue = response.get("classifierConfidence");
                }
                double confidence = confidenceValue instanceof Number
                        ? ((Number) confidenceValue).doubleValue() : 0.0;

                questionLogService.saveLog(new QuestionLog(
                        question, metric, confidence, responseTimeMs,
                        determineOutcome(response)));
            } catch (Exception ignored) {
                // Logging errors must not break /ask.
            }
        }
        return response;
    }

    private String confidenceKey(String question, String metric) {
        return question.trim().toLowerCase(Locale.ROOT) + "|" + metric;
    }

    private Map<String, Object> processQuestion(
            Map<String, String> body) {

        String question = body.get("question");

        if (question == null || question.isBlank()) {
            return Map.of("answer", "Please enter a question.");
        }

        boolean confirmed =
                "true".equalsIgnoreCase(body.get("confirmed"));

        // Handle clinic-count questions before revenue-breakdown routing.
        // Count only clinics with positive revenue in the requested date range.
        if (!confirmed && isClinicRevenueCountQuestion(question)) {
            DateRange dates = resolveDates(question, null);
            if (dates.end().isBefore(dates.start())) {
                return invalidDateRange();
            }
            return countClinicsWithRevenue(question, dates.start(), dates.end());
        }

        // If the user names exactly one scheme, return only that scheme's revenue.
        if (!confirmed) {
            String specificScheme = detectSpecificRevenueScheme(question);
            if (specificScheme != null) {
                DateRange dates = resolveDates(question, null);
                if (dates.end().isBefore(dates.start())) {
                    return invalidDateRange();
                }
                return getSpecificSchemeRevenue(
                        question, specificScheme, dates.start(), dates.end());
            }
        }

        // Fast, deterministic routing for revenue breakdown questions. This avoids
        // sending clear clinic/staff/service revenue questions to the slow LLM fallback.
        if (!confirmed) {
            String directMetric = detectRevenueBreakdownMetric(question);
            if (directMetric != null) {
                DateRange directDates = resolveDates(question, null);
                if (directDates.end().isBefore(directDates.start())) {
                    return invalidDateRange();
                }
                return executeMetric(question, directMetric,
                        directDates.start(), directDates.end(),
                        "Experiment A (direct match)", 1.0);
            }
        }

        String metric;
        String source;
        LocalDate start;
        LocalDate end;

        double confidence = 0.0;

        // Use a confidence supplied by the caller, if present.
        String suppliedConfidence = body.get("confidence");
        if (suppliedConfidence != null && !suppliedConfidence.isBlank()) {
            try {
                confidence = Double.parseDouble(suppliedConfidence);
            } catch (NumberFormatException ignored) {
                confidence = 0.0;
            }
        }

        try {
            if (confirmed) {
                metric = body.get("metric");

                if (metric == null || !SUPPORTED_METRICS.contains(metric)) {
                    return Map.of(
                            "status", "error",
                            "answer", "Please provide a valid supported metric."
                    );
                }

                // Recover confidence if the confirmation request omitted it.
                String key = confidenceKey(question, metric);

                if (confidence <= 0.0) {
                    confidence = pendingConfidences.getOrDefault(key, 0.0);
                }

                pendingConfidences.remove(key);
                source = "Experiment A (confirmed)";

                DateRange dates = resolveDates(question, null);

                start = body.containsKey("start")
                        && body.get("start") != null
                        && !body.get("start").isBlank()
                        ? parseDate(body.get("start"), DEFAULT_START)
                        : dates.start();

                end = body.containsKey("end")
                        && body.get("end") != null
                        && !body.get("end").isBlank()
                        ? parseDate(body.get("end"), DEFAULT_END)
                        : dates.end();

            } else {
                JsonNode prediction = null;

                // Try Experiment A first.
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);

                    HttpEntity<Map<String, String>> request =
                            new HttpEntity<>(
                                    Map.of("question", question),
                                    headers
                            );

                    ResponseEntity<String> classifierResponse =
                            restTemplate.postForEntity(
                                    CLASSIFIER_URL,
                                    request,
                                    String.class
                            );

                    if (classifierResponse.getBody() != null) {
                        prediction = objectMapper.readTree(
                                classifierResponse.getBody()
                        );
                    }
                } catch (Exception ignored) {
                    // If the classifier is unavailable, use Ollama.
                }

                if (prediction != null) {
                    metric = prediction.path("metric").asText("NONE");
                    confidence = prediction.path("confidence").asDouble(0.0);

                    String classifierOutcome =
                            prediction.path("outcome").asText("");

                    if (SUPPORTED_METRICS.contains(metric)
                            && confidence >= HIGH_CONFIDENCE
                            && "ANSWER".equals(classifierOutcome)) {

                        source = "Experiment A";

                    } else if (SUPPORTED_METRICS.contains(metric)
                            && confidence >= MEDIUM_CONFIDENCE
                            && "CONFIRM".equals(classifierOutcome)) {

                        pendingConfidences.put(
                                confidenceKey(question, metric),
                                confidence
                        );

                        Map<String, Object> confirmation =
                                new LinkedHashMap<>();

                        confirmation.put("question", question);
                        confirmation.put(
                                "status", "confirmation_required"
                        );
                        confirmation.put("metric", metric);
                        confirmation.put("confidence", confidence);
                        confirmation.put("classifierConfidence", confidence);
                        confirmation.put("source", "Experiment A");
                        confirmation.put(
                                "answer",
                                "Did you mean this metric? Please confirm."
                        );

                        return confirmation;

                    } else {
                        // Low confidence: use Ollama.
                        source = "Ollama fallback";

                        JsonNode details = getOllamaDetails(question);
                        metric = details.path("metric").asText("NONE");

                        if (!SUPPORTED_METRICS.contains(metric)) {
                            return unsupportedResponse(question);
                        }

                        DateRange dates = resolveDates(question, details);
                        start = dates.start();
                        end = dates.end();

                        if (end.isBefore(start)) {
                            return invalidDateRange();
                        }

                        return executeMetric(
                                question, metric, start, end, source, confidence
                        );
                    }

                } else {
                    // Classifier unavailable: use Ollama.
                    source = "Ollama fallback";

                    JsonNode details = getOllamaDetails(question);
                    metric = details.path("metric").asText("NONE");

                    if (!SUPPORTED_METRICS.contains(metric)) {
                        return unsupportedResponse(question);
                    }

                    DateRange dates = resolveDates(question, details);
                    start = dates.start();
                    end = dates.end();

                    if (end.isBefore(start)) {
                        return invalidDateRange();
                    }

                    return executeMetric(
                            question, metric, start, end, source, confidence
                    );
                }

                // Experiment A selected a metric. Resolve dates locally; do not call
                // Ollama just to parse dates, because that adds unnecessary latency.
                DateRange dates = resolveDates(question, null);

                start = dates.start();
                end = dates.end();

                if (end.isBefore(start)) {
                    return invalidDateRange();
                }
            }

        } catch (Exception e) {
            return Map.of(
                    "question", question,
                    "status", "error",
                    "answer",
                    "Could not interpret the question. Check that the Python classifier and Ollama are running.",
                    "detail",
                    e.getMessage() == null ? "Unknown error" : e.getMessage()
            );
        }

        return executeMetric(
                question, metric, start, end, source, confidence
        );
    }

    private String determineOutcome(Map<String, Object> response) {
        String status = String.valueOf(
                response.getOrDefault("status", "")
        );

        if ("confirmation_required".equals(status)) {
            return "CONFIRMATION_REQUIRED";
        }

        if ("unsupported".equals(status)) {
            return "UNSUPPORTED";
        }

        if ("error".equals(status)) {
            return "ERROR";
        }

        if (response.containsKey("metric")
                && (response.containsKey("value")
                || response.containsKey("total")
                || response.containsKey("data")
                || response.containsKey("answer"))) {
            return "ANSWERED";
        }

        if (response.containsKey("metric")) {
            return "METRIC_SELECTED";
        }

        return "OTHER";
    }

    private Map<String, Object> executeMetric(
            String question,
            String metric,
            LocalDate start,
            LocalDate end,
            String source,
            double confidence) {

        Cache cache = cacheManager.getCache(CACHE_NAME);
        String cacheKey = metric + "|" + start + "|" + end;

        Map<String, Object> metricResult = null;

        if (cache != null) {
            Map<?, ?> cached = cache.get(cacheKey, Map.class);

            if (cached != null) {
                metricResult = new LinkedHashMap<>();

                for (Map.Entry<?, ?> entry : cached.entrySet()) {
                    metricResult.put(
                            String.valueOf(entry.getKey()),
                            entry.getValue()
                    );
                }
            }
        }

        if (metricResult == null) {
            Map<String, Object> result;

            if ("clinic_count".equals(metric)) {
                result = metricsController.clinicCount();
            } else if ("mumbai_dva_versions".equals(metric)) {
                result = metricsController.getMumbaiDvaVersions();
            } else if ("omar_may_details".equals(metric)) {
                result = metricsController.getOmarMayDetails();
            } else {
                result = switch (metric) {
                    case "visits" ->
                            metricsController.getVisits(start, end);
                    case "patients_with_visit" ->
                            metricsController.getPatientsWithVisit(start, end);
                    case "new_patients" ->
                            metricsController.getNewPatients(start, end);
                    case "followup_patients" ->
                            metricsController.getFollowupPatients(start, end);
                    case "bppv_patients" ->
                            metricsController.getBppvPatients(start, end);
                    case "bppv_percentage" ->
                            metricsController.getBppvPercentage(start, end);
                    case "net_neuro_revenue" ->
                            metricsController.getNetNeuroRevenue(start, end);
                    case "revenue_by_scheme" ->
                            metricsController.getRevenueByScheme(start, end);
                    case "revenue_by_staff" ->
                            metricsController.getRevenueByStaff(start, end);
                    case "analysis_amount" ->
                            metricsController.getAnalysisAmount(start, end);
                    case "dva_report_received" ->
                            metricsController.getDvaReportReceived(start, end);
                    case "medicine_line" ->
                            metricsController.getMedicineLines(start, end);
                    case "low_spv_no_torsion_visits" ->
                            metricsController.getLowSpvNoTorsion(start, end);
                    case "visits_by_clinic" ->
                            metricsController.getVisitsByClinic(start, end);
                    case "revenue_by_clinic" ->
                            metricsController.getRevenueByClinic(start, end);
                    case "revenue_by_service" ->
                            metricsController.getRevenueByService(start, end);
                    default -> Map.of(
                            "status", "error",
                            "answer", "Unknown metric selected."
                    );
                };
            }

            metricResult = new LinkedHashMap<>(result);

            if (cache != null && !metricResult.containsKey("status")) {
                cache.put(cacheKey, new LinkedHashMap<>(metricResult));
            }
        }

        // Start with the database result so ordinary breakdown questions retain
        // every value. Apply top/bottom limiting only when the user asks for ranking.
        Map<String, Object> response = new LinkedHashMap<>(metricResult);
        if (isRevenueRankingQuestion(question)
                && ("revenue_by_staff".equals(metric)
                || "revenue_by_clinic".equals(metric)
                || "revenue_by_service".equals(metric))) {
            Map<String, Object> ranked = rankRevenueResults(
                    response, getRequestedResultCount(question),
                    requestsLowestRevenue(question));
            response.clear();
            response.putAll(ranked);
        }

        response.put("question", question);
        response.put("metric", metric);
        response.put("source", source);
        response.put("start", start.toString());
        response.put("end", end.toString());
        response.put("dateLabel", formatDateLabel(start, end));

        if (confidence > 0.0) {
            response.put("confidence", confidence);
            response.put("classifierConfidence", confidence);
        }

        return response;
    }

    private Map<String, Object> unsupportedResponse(String question) {
        return Map.of(
                "question", question,
                "status", "unsupported",
                "answer", "This question is not supported yet."
        );
    }

    private Map<String, Object> invalidDateRange() {
        return Map.of(
                "status", "error",
                "answer", "The end date must not be before the start date."
        );
    }

    private JsonNode getOllamaDetails(String question) throws Exception {
        String prompt = """
                Interpret the clinic database question.
                Return ONLY a JSON object with fields metric, start, end.

                Supported metrics:
                visits, patients_with_visit, new_patients, followup_patients,
                bppv_patients, bppv_percentage, net_neuro_revenue,
                revenue_by_scheme, revenue_by_staff, analysis_amount,
                dva_report_received, medicine_line, low_spv_no_torsion_visits,
                visits_by_clinic, revenue_by_clinic, revenue_by_service,
                clinic_count, mumbai_dva_versions, omar_may_details.

                Dates must use YYYY-MM-DD format.
                For a named month, use its first and last dates.
                If no date is specified, use 2026-05-01 through 2026-05-31.
                If unsupported, set metric to NONE.
                Never invent database values.

                Question: %s
                """.formatted(question);

        String response = ollamaService.ask(prompt);

        if (response == null || response.isBlank()) {
            throw new IllegalStateException(
                    "Ollama returned an empty response."
            );
        }

        response = response.trim()
                .replaceAll("^```(?:json)?\\s*", "")
                .replaceAll("\\s*```$", "");

        return objectMapper.readTree(response);
    }

    private DateRange resolveDates(
            String question,
            JsonNode ollamaDetails) {

        String q = question.toLowerCase(Locale.ROOT)
                .replaceAll("[,?]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        LocalDate today = LocalDate.now();

        if (q.matches(".*\\blast month\\b.*")) {
            YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
            return new DateRange(
                    lastMonth.atDay(1),
                    lastMonth.atEndOfMonth()
            );
        }

        Pattern fullDateRangePattern = Pattern.compile(
                "\\b(?:from\\s+)?(\\d{1,2})\\s+([a-z]+)\\s+(\\d{4})\\s*" +
                "(?:to|through|until|[-–])\\s*(\\d{1,2})\\s+([a-z]+)\\s+(\\d{4})\\b"
        );
        Matcher fullDateRangeMatcher = fullDateRangePattern.matcher(q);

        if (fullDateRangeMatcher.find()) {
            Integer startMonth = MONTHS.get(fullDateRangeMatcher.group(2));
            Integer endMonth = MONTHS.get(fullDateRangeMatcher.group(5));

            if (startMonth != null && endMonth != null) {
                try {
                    LocalDate start = LocalDate.of(
                            Integer.parseInt(fullDateRangeMatcher.group(3)),
                            startMonth,
                            Integer.parseInt(fullDateRangeMatcher.group(1))
                    );

                    LocalDate end = LocalDate.of(
                            Integer.parseInt(fullDateRangeMatcher.group(6)),
                            endMonth,
                            Integer.parseInt(fullDateRangeMatcher.group(4))
                    );

                    return new DateRange(start, end);
                } catch (Exception ignored) {
                    // Continue to the remaining date rules.
                }
            }
        }

        Pattern rangePattern = Pattern.compile(
                "\\b(\\d{1,2})\\s*(?:to|through|until|[-–])\\s*" +
                "(\\d{1,2})\\s+([a-z]+)(?:\\s+(\\d{4}))?\\b"
        );
        Matcher rangeMatcher = rangePattern.matcher(q);

        if (rangeMatcher.find()) {
            Integer month = MONTHS.get(rangeMatcher.group(3));

            if (month != null) {
                int year = rangeMatcher.group(4) == null
                        ? today.getYear()
                        : Integer.parseInt(rangeMatcher.group(4));

                try {
                    LocalDate start = LocalDate.of(
                            year, month,
                            Integer.parseInt(rangeMatcher.group(1))
                    );

                    LocalDate end = LocalDate.of(
                            year, month,
                            Integer.parseInt(rangeMatcher.group(2))
                    );

                    return new DateRange(start, end);
                } catch (Exception ignored) {
                    // Continue to the remaining date rules.
                }
            }
        }

        Pattern isoDateRangePattern = Pattern.compile(
                "\\b(\\d{4}-\\d{2}-\\d{2})\\s*(?:to|through|until|[-–])\\s*" +
                "(\\d{4}-\\d{2}-\\d{2})\\b"
        );
        Matcher isoDateRangeMatcher = isoDateRangePattern.matcher(q);

        if (isoDateRangeMatcher.find()) {
            try {
                return new DateRange(
                        LocalDate.parse(isoDateRangeMatcher.group(1)),
                        LocalDate.parse(isoDateRangeMatcher.group(2))
                );
            } catch (Exception ignored) {
                // Continue to the remaining date rules.
            }
        }

        Pattern monthPattern = Pattern.compile(
                "\\b(january|february|march|april|may|june|july|" +
                "august|september|october|november|december)" +
                "\\s*(\\d{4})?\\b"
        );
        Matcher monthMatcher = monthPattern.matcher(q);

        if (monthMatcher.find()) {
            int month = MONTHS.get(monthMatcher.group(1));
            int year = monthMatcher.group(2) == null
                    ? today.getYear()
                    : Integer.parseInt(monthMatcher.group(2));

            YearMonth yearMonth = YearMonth.of(year, month);

            return new DateRange(
                    yearMonth.atDay(1),
                    yearMonth.atEndOfMonth()
            );
        }

        if (ollamaDetails != null) {
            String modelStart = ollamaDetails.path("start").asText("");
            String modelEnd = ollamaDetails.path("end").asText("");

            if (!modelStart.isBlank() && !modelEnd.isBlank()) {
                try {
                    return new DateRange(
                            LocalDate.parse(modelStart),
                            LocalDate.parse(modelEnd)
                    );
                } catch (Exception ignored) {
                    // Use the defaults below.
                }
            }
        }

        return new DateRange(
                LocalDate.parse(DEFAULT_START),
                LocalDate.parse(DEFAULT_END)
        );
    }

    private String formatDateLabel(LocalDate start, LocalDate end) {
        DateTimeFormatter dayMonth =
                DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH);

        if (start.getDayOfMonth() == 1
                && end.equals(YearMonth.from(start).atEndOfMonth())) {

            String month = start.getMonth()
                    .getDisplayName(TextStyle.FULL, Locale.ENGLISH);

            return "Showing " + month + " " + start.getYear();
        }

        return "Showing "
                + start.format(dayMonth)
                + " to "
                + end.format(dayMonth);
    }

    private LocalDate parseDate(String value, String defaultValue) {
        return LocalDate.parse(
                value == null || value.isBlank() ? defaultValue : value
        );
    }
private record DateRange(LocalDate start, LocalDate end) {
}

private boolean isClinicRevenueCountQuestion(String question) {
    String q = question.toLowerCase(Locale.ROOT);
    boolean asksForCount = Pattern.compile("\\b(how many|number of|count of)\\b").matcher(q).find();
    boolean mentionsClinic = Pattern.compile("\\b(clinic|clinics|branch|branches|centre|centres|center|centers)\\b").matcher(q).find();
    boolean mentionsRevenue = Pattern.compile("\\b(revenue|earnings|income|sales|money|generated|earned|made)\\b").matcher(q).find();
    return asksForCount && mentionsClinic && mentionsRevenue;
}

private Map<String, Object> countClinicsWithRevenue(
        String question, LocalDate start, LocalDate end) {
    Map<String, Object> revenueByClinic = metricsController.getRevenueByClinic(start, end);
    int count = 0;
    for (Object amount : revenueByClinic.values()) {
        if (amount instanceof Number number && number.doubleValue() > 0.0) {
            count++;
        }
    }

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("question", question);
    response.put("metric", "clinic_count");
    response.put("value", count);
    response.put("source", "Experiment A (direct match)");
    response.put("start", start.toString());
    response.put("end", end.toString());
    response.put("dateLabel", formatDateLabel(start, end));
    response.put("confidence", 1.0);
    response.put("classifierConfidence", 1.0);
    return response;
}

private String detectSpecificRevenueScheme(String question) {
    String q = question.toLowerCase(Locale.ROOT);
    boolean mentionsRevenue = Pattern.compile(
            "\\b(revenue|income|sales|amount|earnings|generated|collected|money)\\b")
            .matcher(q).find();
    boolean cghs = Pattern.compile("\\bcghs\\b").matcher(q).find();
    boolean cash = Pattern.compile("\\bcash\\b").matcher(q).find();
    if (!mentionsRevenue || cghs == cash) return null;
    return cghs ? "cghs" : "cash";
}

private Map<String, Object> getSpecificSchemeRevenue(
        String question, String scheme, LocalDate start, LocalDate end) {
    Map<String, Object> allSchemeRevenue = metricsController.getRevenueByScheme(start, end);
    Object amount = allSchemeRevenue.get(scheme);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("question", question);
    response.put("metric", "revenue_by_scheme");
    response.put("scheme", scheme.toUpperCase(Locale.ROOT));
    response.put("value", amount == null ? 0 : amount);
    response.put("source", "Experiment A (direct match)");
    response.put("start", start.toString());
    response.put("end", end.toString());
    response.put("dateLabel", formatDateLabel(start, end));
    response.put("confidence", 1.0);
    response.put("classifierConfidence", 1.0);
    return response;
}

private String detectRevenueBreakdownMetric(String question) {
    String q = question.toLowerCase(Locale.ROOT);
    boolean revenue = q.matches("(?s).*\\b(revenue|earnings|income|sales|money|generated|made|earned|much)\\b.*");
    if (!revenue) return null;

    if (q.matches("(?s).*\\b(clinic|clinics|centre|center|branch|branches)\\b.*")) {
        return "revenue_by_clinic";
    }
    if (q.matches("(?s).*\\b(staff|employee|employees|doctor|doctors|provider|providers)\\b.*")) {
        return "revenue_by_staff";
    }
    if (q.matches("(?s).*\\b(service|services|treatment|treatments)\\b.*")) {
        return "revenue_by_service";
    }
    return null;
}

private boolean isRevenueRankingQuestion(String question) {
    return Pattern.compile("(?i)\\b(top|bottom|highest|lowest|most|least|last)\\b")
            .matcher(question).find();
}

private Map<String, Object> rankRevenueResults(
        Map<String, Object> data, int count, boolean lowestFirst) {
    List<Map.Entry<String, Object>> entries = new ArrayList<>();
    for (Map.Entry<String, Object> entry : data.entrySet()) {

        if (entry.getValue() instanceof Number) entries.add(entry);
    }
    Comparator<Map.Entry<String, Object>> comparator = Comparator.comparingDouble(
            entry -> ((Number) entry.getValue()).doubleValue());
    if (!lowestFirst) comparator = comparator.reversed();
    entries.sort(comparator);

    Map<String, Object> ranked = new LinkedHashMap<>();
    for (int i = 0; i < Math.min(count, entries.size()); i++) {
        ranked.put(entries.get(i).getKey(), entries.get(i).getValue());
    }
    return ranked;


}



private int getRequestedResultCount(String question) {
    String q = question.toLowerCase(Locale.ROOT);

    // First, detect numbers written as digits after ranking words.
    Matcher digits = Pattern.compile(
            "(?i)\\b(?:top|bottom|highest|lowest|most|least|last)\\s+(\\d+)\\b")
            .matcher(q);

    if (digits.find()) {
        return Math.max(1, Math.min(100,
                Integer.parseInt(digits.group(1))));
    }

    // Detect numbers written as words after ranking words.
    Matcher rankingWords = Pattern.compile(
            "\\b(?:top|bottom|highest|lowest|most|least|last)\\s+"
            + "(one|two|three|four|five|six|seven|eight|nine|ten)\\b")
            .matcher(q);

    if (rankingWords.find()) {
        return wordToCount(rankingWords.group(1));
    }

    // Detect wording such as "Which two clinics..."
    Matcher beforeSubject = Pattern.compile(
            "\\b(?:which|what)\\s+(one|two|three|four|five|six|seven|eight|nine|ten|\\d+)\\b")
            .matcher(q);

    if (beforeSubject.find()) {
        String value = beforeSubject.group(1);
        if (value.matches("\\d+")) {
            return Math.max(1, Math.min(100, Integer.parseInt(value)));
        }
        return wordToCount(value);
    }

    // Singular "most/least" questions should return one result.
    if (Pattern.compile(
            "\\b(highest|lowest|most|least|maximum|minimum|greatest|smallest)\\b")
            .matcher(q).find()) {
        return 1;
    }

    return 2;
}

private int wordToCount(String word) {
    return switch (word.toLowerCase(Locale.ROOT)) {
        case "one" -> 1; 
        case "two" -> 2;
        case "three" -> 3;
        case "four" -> 4;
        case "five" -> 5;
        case "six" -> 6;
        case "seven" -> 7;
        case "eight" -> 8;
        case "nine" -> 9;
        default -> 10;
    };
}


private boolean requestsLowestRevenue(String question) {
    return Pattern.compile("(?i)\\b(bottom|lowest|least|last)\\b")
            .matcher(question).find();
}

    }

