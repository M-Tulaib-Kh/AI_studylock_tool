package com.studylock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OpenRouterService {

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String OPENROUTER_MODEL = "meta-llama/llama-3.1-8b-instruct:free";

    @Value("${openrouter.api.key}")
    private String apiKey;

    private final WebClient webClient = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(
            HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 20000)
                .responseTimeout(Duration.ofSeconds(90))
                .doOnConnected(conn ->
                    conn.addHandlerLast(new ReadTimeoutHandler(90, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(20, TimeUnit.SECONDS)))
        ))
        .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
        .build();

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong lastCallTime = new AtomicLong(0);
    private static final long MIN_GAP_MS = 1500;

    private String callAI(String systemPrompt, String userPrompt) {
        enforceCallGap();
        Map<String, Object> sysMsg  = Map.of("role", "system", "content", systemPrompt);
        Map<String, Object> userMsg = Map.of("role", "user",   "content", userPrompt);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", OPENROUTER_MODEL);
        body.put("messages", List.of(sysMsg, userMsg));
        body.put("max_tokens", 4096);
        body.put("temperature", 0.3);
        int maxRetries = 3;
        int[] delays   = {3000, 6000, 12000};
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String response = webClient.post()
                    .uri(OPENROUTER_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://studylock.app")
                    .header("X-Title", "StudyLock")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(90))
                    .block();
                lastCallTime.set(System.currentTimeMillis());
                JsonNode root = mapper.readTree(response);
                if (root.has("error")) {
                    String msg  = root.path("error").path("message").asText("API error");
                    int    code = root.path("error").path("code").asInt(0);
                    if (code == 429) { if (attempt < maxRetries-1) { sleep(delays[attempt]); continue; } throw new RuntimeException("AI rate limit reached. Please wait a moment."); }
                    if (code == 401) throw new RuntimeException("Invalid OpenRouter API key.");
                    throw new RuntimeException("AI error: " + msg);
                }
                String text = root.path("choices").path(0).path("message").path("content").asText("");
                if (text.isBlank()) throw new RuntimeException("Empty AI response. Please try again.");
                return text;
            } catch (WebClientResponseException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    if (attempt < maxRetries-1) { sleep(delays[attempt]); continue; }
                    throw new RuntimeException("AI rate limit. Please try again in a moment.");
                }
                if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) throw new RuntimeException("Invalid API key.");
                if (attempt < maxRetries-1) { sleep(delays[attempt]); continue; }
                throw new RuntimeException("AI request failed: " + e.getStatusCode());
            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().contains("key")) throw e;
                if (attempt < maxRetries-1) { sleep(delays[attempt]); continue; }
                throw e;
            } catch (Exception e) {
                if (attempt < maxRetries-1) { sleep(delays[attempt]); continue; }
                throw new RuntimeException("AI unavailable: " + e.getMessage());
            }
        }
        throw new RuntimeException("AI unavailable after retries. Please try again.");
    }

    // ── Conversation-aware AI call (for tutor) ─────────────────
    private String callAIWithHistory(String systemPrompt, List<Map<String,Object>> history) {
        enforceCallGap();
        List<Map<String,Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(history);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", OPENROUTER_MODEL);
        body.put("messages", messages);
        body.put("max_tokens", 2048);
        body.put("temperature", 0.5);
        int maxRetries = 2;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String response = webClient.post()
                    .uri(OPENROUTER_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://studylock.app")
                    .header("X-Title", "StudyLock")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(90))
                    .block();
                lastCallTime.set(System.currentTimeMillis());
                JsonNode root = mapper.readTree(response);
                String text = root.path("choices").path(0).path("message").path("content").asText("");
                if (text.isBlank()) throw new RuntimeException("Empty AI response.");
                return text;
            } catch (Exception e) {
                if (attempt < maxRetries - 1) { sleep(3000); continue; }
                throw new RuntimeException("AI tutor unavailable: " + e.getMessage());
            }
        }
        throw new RuntimeException("AI tutor unavailable.");
    }

    private void enforceCallGap() {
        long gap = System.currentTimeMillis() - lastCallTime.get();
        if (gap < MIN_GAP_MS) sleep(MIN_GAP_MS - gap);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private String extractJson(String text) {
        text = text.replaceAll("(?s)```json", "").replaceAll("```", "").trim();
        int arr = text.indexOf('['), obj = text.indexOf('{');
        int start, end;
        if (arr >= 0 && (obj < 0 || arr < obj)) { start = arr; end = text.lastIndexOf(']'); }
        else if (obj >= 0)                        { start = obj; end = text.lastIndexOf('}'); }
        else throw new RuntimeException("No JSON in AI response. Please try again.");
        if (end < start) throw new RuntimeException("Malformed AI response. Please try again.");
        return text.substring(start, end + 1);
    }

    private String sanitize(String s) {
        if (s == null) return "";
        String c = s.replaceAll("[<>{}]", "").trim();
        return c.length() > 500 ? c.substring(0, 500) : c;
    }

    // ── Generate Quiz Questions (English only, exact count) ───
    public String generateQuiz(String subject, String prompt, int count) {
        int n = Math.min(Math.max(count, 1), 15);
        String sys = "You are an academic quiz generator. ALWAYS respond in English only, regardless of the question language. Return ONLY valid JSON array, no markdown, no explanation, no extra text.";
        String user = String.format(
            "Subject: \"%s\". Topic: \"%s\". Generate EXACTLY %d MCQ questions in English.\n" +
            "Return a JSON array with exactly %d items: [{\"id\":\"q1\",\"questionText\":\"...\",\"optionA\":\"...\",\"optionB\":\"...\",\"optionC\":\"...\",\"optionD\":\"...\",\"correctAnswer\":\"A\",\"marks\":2}]\n" +
            "correctAnswer must be A, B, C, or D. All text must be in English. No extra text outside the JSON array.",
            sanitize(subject), sanitize(prompt), n, n);
        return extractJson(callAI(sys, user));
    }

    // ── Generate Assignment ──────────────────────────────────
    public Map<String, String> generateAssignment(String subject, String prompt) {
        String sys  = "You are an academic assignment creator. ALWAYS respond in English. Return ONLY valid JSON, no markdown.";
        String user = String.format(
            "Subject: \"%s\". Topic: \"%s\".\n" +
            "Return in English: {\"title\":\"...\",\"description\":\"2-3 sentence overview\",\"instructions\":\"numbered steps\"}",
            sanitize(subject), sanitize(prompt));
        try {
            JsonNode node = mapper.readTree(extractJson(callAI(sys, user)));
            return Map.of(
                "title",        node.path("title").asText("Assignment"),
                "description",  node.path("description").asText(""),
                "instructions", node.path("instructions").asText(""));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate assignment: " + e.getMessage());
        }
    }

    // ── Grade Submission ─────────────────────────────────────
    public Map<String, Object> gradeSubmission(String assignmentPrompt, String notes, int totalMarks) {
        String sys  = "You are an academic grader. ALWAYS respond in English. Return ONLY valid JSON.";
        String user = String.format(
            "Assignment: \"%s\". Notes: \"%s\". Total marks: %d.\n" +
            "Return in English: {\"marks\":0,\"feedback\":\"constructive 3-4 sentence feedback\"}\n" +
            "marks must be 0 to %d.",
            sanitize(assignmentPrompt), sanitize(notes), totalMarks, totalMarks);
        try {
            JsonNode node = mapper.readTree(extractJson(callAI(sys, user)));
            int marks = Math.min(Math.max(node.path("marks").asInt(0), 0), totalMarks);
            return Map.of("marks", marks, "feedback", node.path("feedback").asText("Good work."));
        } catch (Exception e) {
            throw new RuntimeException("AI grading failed: " + e.getMessage());
        }
    }

    // ── Grade Quiz Attempt ───────────────────────────────────
    public Map<String, Object> gradeQuiz(String questionsJson, String answersJson) {
        String sys  = "You are a quiz evaluator. ALWAYS respond in English. Return ONLY valid JSON.";
        String user = String.format(
            "Questions: %s\nStudent answers (questionId→selected option): %s\n" +
            "Return in English: {\"score\":0,\"feedback\":\"personalized 2-3 sentence feedback\"}",
            questionsJson, answersJson);
        try {
            JsonNode node = mapper.readTree(extractJson(callAI(sys, user)));
            return Map.of("score", node.path("score").asInt(0), "feedback", node.path("feedback").asText(""));
        } catch (Exception e) {
            return Map.of("score", 0, "feedback", "AI feedback unavailable.");
        }
    }

    // ── Generate Study Plan ──────────────────────────────────
    public String generateStudyPlan(String subject, String goals, int weeks) {
        int n = Math.min(weeks, 12);
        String sys  = "You are an expert academic study planner. ALWAYS respond in English. Return ONLY a valid JSON array, no markdown.";
        String user = String.format(
            "Subject: \"%s\". Goals: \"%s\". Duration: %d weeks.\n" +
            "Return in English: [{\"week\":1,\"title\":\"...\",\"topics\":[\"...\"],\"tasks\":[\"...\"],\"dailyHours\":2}]\n" +
            "Each item: week (int), title (string), topics (string array), tasks (string array), dailyHours (int).",
            sanitize(subject), sanitize(goals), n);
        return extractJson(callAI(sys, user));
    }

    // ── Summarize Lecture ────────────────────────────────────
    public String summarizeLecture(String title, String description, String subject) {
        String sys  = "You are an academic content summarizer. ALWAYS respond in English. Return ONLY valid JSON, no markdown.";
        String user = String.format(
            "Lecture: \"%s\". Subject: \"%s\". Description: \"%s\".\n" +
            "Return in English: {\"summary\":\"2-3 paragraphs\",\"keyPoints\":[\"...\"],\"concepts\":[\"...\"],\"studyTips\":[\"...\"]}",
            sanitize(title), sanitize(subject), sanitize(description));
        return extractJson(callAI(sys, user));
    }

    // ── AI Tutor (conversational) ────────────────────────────
    public String askTutor(String subject, String question, List<Map<String,Object>> conversationHistory) {
        String sys = "You are StudyLock AI Tutor — a friendly, expert academic tutor. " +
            "ALWAYS respond in English only, regardless of what language the student uses. " +
            "If asked in Urdu or any other language, politely note you respond in English and answer in English. " +
            "Give clear, educational, step-by-step explanations. " +
            "Current subject context: " + sanitize(subject) + ". " +
            "Be encouraging, accurate, and concise. Use examples where helpful.";
        List<Map<String,Object>> history = new ArrayList<>(conversationHistory);
        history.add(Map.of("role", "user", "content", sanitize(question)));
        return callAIWithHistory(sys, history.subList(Math.max(0, history.size()-10), history.size()));
    }
}
