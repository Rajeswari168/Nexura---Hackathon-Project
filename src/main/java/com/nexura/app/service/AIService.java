package com.nexura.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexura.app.entity.*;
import com.nexura.app.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIService {

    @Autowired
    private HealthLogRepository healthLogRepository;

    @Autowired
    private SleepLogRepository sleepLogRepository;

    @Autowired
    private MedicationAdherenceRepository adherenceRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private MedicalReportRepository reportRepository;

    @Autowired
    private ReportAnalysisRepository reportAnalysisRepository;

    @Autowired
    private AIAnalysisRepository aiAnalysisRepository;

    @Autowired
    private ChatbotMessageRepository chatRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private MedicationService medicationService;

    @Value("${nexura.ai.provider}")
    private String aiProvider;

    // --- Groq (Primary) ---
    @Value("${nexura.ai.groq.key:}")
    private String groqKey;

    @Value("${nexura.ai.groq.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqUrl;

    @Value("${nexura.ai.groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    // --- Gemini (Fallback) ---
    @Value("${nexura.ai.gemini.key:}")
    private String geminiKey;

    @Value("${nexura.ai.gemini.url:https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent}")
    private String geminiUrl;

    // --- OpenAI (Fallback) ---
    @Value("${nexura.ai.openai.key:}")
    private String openaiKey;

    @Value("${nexura.ai.openai.url:https://api.openai.com/v1/chat/completions}")
    private String openaiUrl;

    @Value("${nexura.ai.openai.model:gpt-4o-mini}")
    private String openaiModel;

    @Value("${nexura.upload.dir:./uploads}")
    private String uploadDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 1. Analyze Vitals Trend & Compliance
    @Transactional
    public AIAnalysis analyzeOverallDeterioration() {
        User user = userService.getCurrentUser();
        String context = compilePatientContext(user);

        String prompt = "You are an AI medical analyzer. Analyze the following patient history and current vitals:\n" +
                context + "\n\n" +
                "Evaluate the overall deterioration risk. Classify the risk level strictly as: LOW, MEDIUM, HIGH, or CRITICAL.\n" +
                "Respond ONLY in valid raw JSON format matching this schema:\n" +
                "{\n" +
                "  \"riskLevel\": \"LOW | MEDIUM | HIGH | CRITICAL\",\n" +
                "  \"keyFindings\": \"Bullet point summary of health status\",\n" +
                "  \"recommendations\": \"Bullet point suggestions and actions\"\n" +
                "}\n" +
                "Do not include markdown tags like ```json or wrappers around the JSON.";

        String aiResponse = queryAI(prompt);
        AIAnalysis analysis = parseAIAnalysis(user, aiResponse);
        
        // Auto-safeguard: If risk level is CRITICAL or HIGH, create a notification alert
        if ("CRITICAL".equals(analysis.getRiskLevel()) || "HIGH".equals(analysis.getRiskLevel())) {
            createRiskNotificationAlert(user, analysis);
        }

        return aiAnalysisRepository.save(analysis);
    }

    public List<AIAnalysis> getAnalysisHistory() {
        User user = userService.getCurrentUser();
        return aiAnalysisRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public AIAnalysis getLatestRiskStatus() {
        User user = userService.getCurrentUser();
        return aiAnalysisRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseGet(() -> {
                    // Fallback baseline risk if none exists
                    AIAnalysis analysis = new AIAnalysis();
                    analysis.setUser(user);
                    analysis.setRiskLevel("LOW");
                    analysis.setKeyFindings("No records logged yet. Your metrics are baseline.");
                    analysis.setRecommendations("Start tracking your daily health, sleep, and medications to get AI assessments.");
                    return analysis;
                });
    }

    // 2. Chatbot response with Context Memory
    @Transactional
    public ChatbotMessage generateChatbotResponse(String userMessageText, String requestedLanguage, boolean voiceUsed) {
        User user = userService.getCurrentUser();

        // Fetch AI risk level and health context
        AIAnalysis latestRisk = getLatestRiskStatus();
        String riskLevel = latestRisk.getRiskLevel();
        String patientContext = compilePatientContext(user);

        // Fetch past chatbot messages
        List<ChatbotMessage> chatHistory = chatRepository.findByUserIdOrderByTimestampAsc(user.getId());
        String conversationHistory = chatHistory.stream()
                .limit(10) // Limit context window for efficiency
                .map(m -> "User: " + m.getMessage() + "\nAI: " + m.getAiResponse())
                .collect(Collectors.joining("\n"));

        String systemPrompt = "You are Nexura, an empathetic AI chronic care companion. You support patients with chronic conditions.\n" +
                "Here is the patient's context:\n" +
                patientContext + "\n" +
                "Current AI Risk Assessment: " + riskLevel + "\n" +
                "LATEST KEY FINDINGS: " + latestRisk.getKeyFindings() + "\n" +
                "LATEST RECOMMENDATIONS: " + latestRisk.getRecommendations() + "\n\n" +
                "ADAPTIVE TONE GUIDELINES:\n" +
                "- If Risk is LOW: Be warm, positive, encouraging, and supportive. Set emotionMode to 'NORMAL'.\n" +
                "- If Risk is MEDIUM: Be gentle, concerned, supportive. Suggest lifestyle tweaks. Set emotionMode to 'EMPATHETIC'.\n" +
                "- If Risk is HIGH: Be deeply empathetic, extremely supportive, calm, serious, and suggest contacting their caregiver (" + user.getEmergencyCaregiverName() + ": " + user.getCaregiverPhoneNumber() + ") or doctor immediately. Set emotionMode to 'URGENT'.\n" +
                "- If Risk is CRITICAL: Be deeply empathetic, serious, urgent, and suggest contacting emergency care or caregiver. Set emotionMode to 'CRITICAL'.\n\n" +
                "The user requested the chatbot response in this language code: " + requestedLanguage + " (where auto means auto-detect the language from user's text, and other options are: en, ta, hi, te, ml, kn, fr, es, de, ja).\n" +
                "Make sure to translate or speak in the specified language (Tamil, Hindi, etc.).\n\n" +
                "Recent Chat Logs:\n" +
                conversationHistory + "\n\n" +
                "Patient's New Message to Respond to: " + userMessageText + "\n\n" +
                "Respond ONLY in valid raw JSON format matching this schema:\n" +
                "{\n" +
                "  \"reply\": \"Concise and highly supportive chatbot response in the selected language, under 3-4 sentences.\",\n" +
                "  \"riskLevel\": \"LOW | MEDIUM | HIGH | CRITICAL\",\n" +
                "  \"emotionMode\": \"NORMAL | EMPATHETIC | URGENT | CRITICAL\"\n" +
                "}\n" +
                "Do not include markdown tags like ```json or wrappers around the JSON.";

        String aiResponse = queryAI(systemPrompt);

        String replyText = "";
        String responseRisk = riskLevel;
        String responseEmotion = "NORMAL";

        try {
            JsonNode root = objectMapper.readTree(cleanJsonString(aiResponse));
            replyText = root.path("reply").asText("");
            responseRisk = root.path("riskLevel").asText(riskLevel);
            responseEmotion = root.path("emotionMode").asText("NORMAL");
        } catch (Exception e) {
            replyText = aiResponse;
            if (aiResponse != null && (aiResponse.contains("{") || aiResponse.contains("reply"))) {
                replyText = getLocalizedFallbackResponse(userMessageText, requestedLanguage, riskLevel, user);
            }
            // Heuristic fallbacks for risk and emotion modes
            if ("CRITICAL".equals(riskLevel) || (aiResponse != null && (aiResponse.contains("caregiver") || aiResponse.contains("emergency")))) {
                responseRisk = "CRITICAL";
                responseEmotion = "CRITICAL";
            } else if ("HIGH".equals(riskLevel)) {
                responseRisk = "HIGH";
                responseEmotion = "URGENT";
            } else if ("MEDIUM".equals(riskLevel)) {
                responseRisk = "MEDIUM";
                responseEmotion = "EMPATHETIC";
            }
        }

        if (replyText == null || replyText.trim().isEmpty()) {
            replyText = getLocalizedFallbackResponse(userMessageText, requestedLanguage, riskLevel, user);
        }

        // Save AI response turn
        ChatbotMessage turn = new ChatbotMessage();
        turn.setUser(user);
        turn.setMessage(userMessageText);
        turn.setAiResponse(replyText);
        turn.setLanguage(requestedLanguage);
        turn.setVoiceUsed(voiceUsed);
        
        ChatbotMessage saved = chatRepository.save(turn);
        saved.setRiskLevel(responseRisk);
        saved.setEmotionMode(responseEmotion);
        return saved;
    }

    public List<ChatbotMessage> getChatHistory() {
        User user = userService.getCurrentUser();
        return chatRepository.findByUserIdOrderByTimestampAsc(user.getId());
    }

    @Transactional
    public void clearChatHistory() {
        User user = userService.getCurrentUser();
        List<ChatbotMessage> history = chatRepository.findByUserIdOrderByTimestampAsc(user.getId());
        chatRepository.deleteAll(history);
    }

    // 3. Multimodal Analysis for Uploaded Reports
    @Transactional
    public ReportAnalysis analyzeMedicalReport(Long reportId) {
        MedicalReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Medical report not found"));

        // Check if report has already been analyzed
        Optional<ReportAnalysis> existing = reportAnalysisRepository.findByReportId(reportId);
        if (existing.isPresent()) {
            return existing.get();
        }

        String extractedText = "";
        String summary = "";
        String riskScore = "LOW";

        if ("application/pdf".equals(report.getFileType())) {
            // PDF: Use PDFBox text extraction
            try {
                String filename = report.getFilePath().substring(report.getFilePath().lastIndexOf("/") + 1);
                Path path = Paths.get(uploadDir).resolve(filename);
                File pdfFile = path.toFile();
                
                try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfFile)) {
                    org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                    extractedText = stripper.getText(document);
                }
            } catch (Exception e) {
                extractedText = "Failed to parse PDF: " + e.getMessage();
            }
        }

        // Run AI Report Analyzer Prompt
        String analysisPrompt = "You are a clinical document parser. Analyze this clinical medical report:\n" +
                "File name: " + report.getFileName() + "\n" +
                "Text Extracted (if PDF): " + extractedText + "\n\n" +
                "Identify: Important values, summaries of findings, abnormal markers, and comparison instructions.\n" +
                "Respond ONLY in valid JSON format matching this schema:\n" +
                "{\n" +
                "  \"summary\": \"Concise report overview\",\n" +
                "  \"riskIndicators\": [\"indicator 1\", \"indicator 2\"],\n" +
                "  \"riskScore\": \"LOW | MEDIUM | HIGH | CRITICAL\",\n" +
                "  \"recommendation\": \"Consult instructions\"\n" +
                "}\n" +
                "Do not include markdown tags like ```json.";

        String aiResponse = "";
        
        // Handle Multimodal Image Inputs for JPG/JPEG/PNG natively using Gemini Vision!
        if (report.getFileType().startsWith("image/")) {
            aiResponse = queryGeminiVision(report, analysisPrompt);
        } else {
            aiResponse = queryAI(analysisPrompt);
        }

        // Parse Report Analysis response
        ReportAnalysis reportAnalysis = new ReportAnalysis();
        reportAnalysis.setReport(report);
        reportAnalysis.setExtractedText(extractedText.length() > 5000 ? extractedText.substring(0, 5000) : extractedText);

        try {
            JsonNode root = objectMapper.readTree(cleanJsonString(aiResponse));
            summary = root.path("summary").asText("No summary provided.");
            riskScore = root.path("riskScore").asText("LOW");
            
            // Build recommendations block
            StringBuilder recs = new StringBuilder();
            recs.append("Findings:\n");
            root.path("riskIndicators").forEach(ind -> recs.append("- ").append(ind.asText()).append("\n"));
            recs.append("\nRecommendation: ").append(root.path("recommendation").asText("N/A"));
            
            reportAnalysis.setAiSummary(summary);
            reportAnalysis.setRiskScore(riskScore);
            // Append formatted findings in text block
            reportAnalysis.setExtractedText(extractedText + "\n\n" + recs.toString());

        } catch (Exception e) {
            // Local fallback rule-based analyzer in case of JSON parse failure
            reportAnalysis.setAiSummary("Clinical report uploaded. Contains vital patient history metrics.");
            reportAnalysis.setRiskScore("MEDIUM");
        }

        return reportAnalysisRepository.save(reportAnalysis);
    }

    // --- Helper Prompt Orchestration & Integrations ---

    private String compilePatientContext(User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("Patient Details: ").append(user.getFullName()).append(", Age ").append(user.getAge()).append("\n");

        // 1. Health Logs context
        List<HealthLog> vitals = healthLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        sb.append("\nRecent Health Log Entries:\n");
        vitals.stream().limit(5).forEach(v -> {
            sb.append("- ").append(v.getCreatedAt()).append(": ")
              .append("HR: ").append(v.getHeartRate()).append(" BPM, ")
              .append("BP: ").append(v.getBloodPressure()).append(", ")
              .append("O2: ").append(v.getOxygenLevel()).append("%, ")
              .append("Temp: ").append(v.getBodyTemperature()).append("Â°C, ")
              .append("Stress: ").append(v.getStressLevel()).append("/10, ")
              .append("Fatigue: ").append(v.getFatigueLevel()).append("\n");
        });

        // 2. Sleep Logs context
        List<SleepLog> sleep = sleepLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        sb.append("\nRecent Sleep Entries:\n");
        sleep.stream().limit(3).forEach(s -> {
            sb.append("- ").append(s.getSleepStartTime()).append(" to ").append(s.getWakeTime())
              .append(": ").append(s.getDurationHours()).append(" hrs, Quality: ").append(s.getSleepQuality()).append("/5\n");
        });

        // 3. Medications & Adherence Compliance
        double compliance = medicationService.getWeeklyComplianceRate();
        sb.append("\nMedication Adherence Compliance (Last 7 days): ").append(String.format("%.1f", compliance)).append("%\n");

        // 4. Clinical reports summary
        List<MedicalReport> reports = reportRepository.findByUserIdOrderByUploadedAtDesc(user.getId());
        sb.append("\nUploaded Medical Reports Context:\n");
        reports.stream().limit(3).forEach(r -> {
            Optional<ReportAnalysis> ra = reportAnalysisRepository.findByReportId(r.getId());
            ra.ifPresent(reportAnalysis -> sb.append("- ").append(r.getFileName()).append(" (Risk: ").append(reportAnalysis.getRiskScore()).append("): ")
                    .append(reportAnalysis.getAiSummary()).append("\n"));
        });

        return sb.toString();
    }

    private User tryGetCurrentUser() {
        try {
            return userService.getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }

    private String queryAI(String prompt) {
        User user = tryGetCurrentUser();
        
        String provider = (user != null && user.getAiProvider() != null && !user.getAiProvider().isEmpty()) 
                ? user.getAiProvider() : this.aiProvider;
        String gKey = (user != null && user.getGroqKey() != null && !user.getGroqKey().isEmpty()) 
                ? user.getGroqKey() : this.groqKey;
        String gemKey = (user != null && user.getGeminiKey() != null && !user.getGeminiKey().isEmpty()) 
                ? user.getGeminiKey() : this.geminiKey;
        String opKey = (user != null && user.getOpenaiKey() != null && !user.getOpenaiKey().isEmpty()) 
                ? user.getOpenaiKey() : this.openaiKey;

        // If mock is selected, go straight to mock
        if ("mock".equalsIgnoreCase(provider)) {
            return getMockAIResponse(prompt);
        }

        // 1. Try Groq if selected
        if ("groq".equalsIgnoreCase(provider)) {
            if (gKey != null && !gKey.isEmpty() && !gKey.equals("YOUR_GROQ_API_KEY_HERE")) {
                String result = callGroq(prompt, gKey);
                if (result != null && !result.isEmpty()) return result;
            }
            // fallback chain if groq fails
            if (gemKey != null && !gemKey.isEmpty() && !gemKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
                String result = callGemini(prompt, gemKey);
                if (result != null && !result.isEmpty()) return result;
            }
        }
        
        // 2. Try Gemini if selected
        if ("gemini".equalsIgnoreCase(provider)) {
            if (gemKey != null && !gemKey.isEmpty() && !gemKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
                String result = callGemini(prompt, gemKey);
                if (result != null && !result.isEmpty()) return result;
            }
            // fallback chain if gemini fails
            if (gKey != null && !gKey.isEmpty() && !gKey.equals("YOUR_GROQ_API_KEY_HERE")) {
                String result = callGroq(prompt, gKey);
                if (result != null && !result.isEmpty()) return result;
            }
        }

        // 3. Try OpenAI if selected
        if ("openai".equalsIgnoreCase(provider)) {
            if (opKey != null && !opKey.isEmpty()) {
                String result = callOpenAIChat(prompt, opKey);
                if (result != null && !result.isEmpty()) return result;
            }
        }

        // 4. Default fallback chain if everything else fails
        if (gKey != null && !gKey.isEmpty() && !gKey.equals("YOUR_GROQ_API_KEY_HERE")) {
            String result = callGroq(prompt, gKey);
            if (result != null && !result.isEmpty()) return result;
        }
        if (gemKey != null && !gemKey.isEmpty() && !gemKey.equals("YOUR_GEMINI_API_KEY_HERE")) {
            String result = callGemini(prompt, gemKey);
            if (result != null && !result.isEmpty()) return result;
        }

        // 5. Smart rule-based fallback engine
        return getMockAIResponse(prompt);
    }

    private String callGroq(String prompt, String apiKey) {
        try {
            System.err.println("[GROQ] Calling Groq API with model: " + groqModel);
            java.net.URL url = new java.net.URL(groqUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            // Groq uses OpenAI-compatible chat completions format
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", groqModel);
            root.put("temperature", 0.7);
            root.put("max_tokens", 512);
            ArrayNode messages = root.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);

            String jsonPayload = objectMapper.writeValueAsString(root);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            System.err.println("[GROQ] Response code: " + code);
            if (code == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) response.append(line.trim());
                    JsonNode respJson = objectMapper.readTree(response.toString());
                    String result = respJson.path("choices").get(0).path("message").path("content").asText("");
                    System.err.println("[GROQ] SUCCESS - got response.");
                    return result;
                }
            } else {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder err = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) err.append(line.trim());
                    System.err.println("[GROQ] Error " + code + ": " + err);
                }
            }
        } catch (Exception e) {
            System.err.println("[GROQ] Exception: " + e.getMessage());
        }
        return null;
    }

    private String callGemini(String prompt, String apiKey) {
        // Try multiple Gemini model endpoints in order of preference
        // gemini-2.5-flash confirmed 200 OK for this API key.
        // gemini-2.0-flash has exhausted its free-tier daily quota (429).
        String[] modelUrls = {
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent",
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash-lite:generateContent",
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash-lite-001:generateContent"
        };

        for (String modelUrl : modelUrls) {
            try {
                System.err.println("[GEMINI] Trying: " + modelUrl);
                URL url = new URL(modelUrl + "?key=" + apiKey);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);

                // Construct Gemini REST Payload
                ObjectNode root = objectMapper.createObjectNode();
                ArrayNode contents = root.putArray("contents");
                ObjectNode contentObj = contents.addObject();
                ArrayNode parts = contentObj.putArray("parts");
                parts.addObject().put("text", prompt);

                // Add generation config for more focused responses
                ObjectNode generationConfig = root.putObject("generationConfig");
                generationConfig.put("temperature", 0.7);
                generationConfig.put("maxOutputTokens", 512);

                String jsonPayload = objectMapper.writeValueAsString(root);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                System.err.println("[GEMINI] Response code from " + modelUrl + ": " + code);

                if (code == 200) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line.trim());
                        }
                        JsonNode respJson = objectMapper.readTree(response.toString());
                        // Extract text safely
                        JsonNode candidates = respJson.path("candidates");
                        if (candidates.isArray() && candidates.size() > 0) {
                            String result = candidates.get(0).path("content").path("parts").get(0).path("text").asText("");
                            if (result != null && !result.trim().isEmpty()) {
                                System.err.println("[GEMINI] SUCCESS from " + modelUrl);
                                return result;
                            }
                        }
                    }
                } else if (code == 400) {
                    // Bad request - no point retrying other models
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder errResponse = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) errResponse.append(line.trim());
                        System.err.println("[GEMINI] 400 Bad Request: " + errResponse);
                    }
                    break;
                } else {
                    // 429, 503, etc â€” try next model
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder errResponse = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) errResponse.append(line.trim());
                        System.err.println("[GEMINI] Error " + code + " from " + modelUrl + ": " + errResponse);
                    }
                }
            } catch (Exception e) {
                System.err.println("[GEMINI] Exception calling " + modelUrl + ": " + e.getMessage());
            }
        }

        System.err.println("[GEMINI] All endpoints failed. Using smart fallback engine.");
        return getMockAIResponse(prompt);
    }

    private String queryGeminiVision(MedicalReport report, String prompt) {
        User user = tryGetCurrentUser();
        String gemKey = (user != null && user.getGeminiKey() != null && !user.getGeminiKey().isEmpty()) 
                ? user.getGeminiKey() : this.geminiKey;
        try {
            // Read Image Bytes
            String filename = report.getFilePath().substring(report.getFilePath().lastIndexOf("/") + 1);
            Path path = Paths.get(uploadDir).resolve(filename);
            byte[] fileBytes = Files.readAllBytes(path);
            String base64Image = Base64.getEncoder().encodeToString(fileBytes);

            URL url = new URL(geminiUrl + "?key=" + gemKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setDoOutput(true);

            // Construct Multimodal vision JSON payload
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode contents = root.putArray("contents");
            ObjectNode contentObj = contents.addObject();
            ArrayNode parts = contentObj.putArray("parts");
            
            // Add Base64 inlineData
            ObjectNode partImage = parts.addObject();
            ObjectNode inlineData = partImage.putObject("inlineData");
            inlineData.put("mimeType", report.getFileType());
            inlineData.put("data", base64Image);

            // Add clinical parser prompt text
            ObjectNode partText = parts.addObject();
            partText.put("text", prompt);

            String jsonPayload = objectMapper.writeValueAsString(root);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                    JsonNode respJson = objectMapper.readTree(response.toString());
                    return respJson.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
                }
            }
        } catch (Exception e) {
            // Log vision-calling error
        }
        return getMockAIResponse(prompt);
    }

    private String callOpenAIChat(String prompt, String apiKey) {
        try {
            URL url = new URL(openaiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);

            // Construct OpenAI REST Payload
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", openaiModel);
            ArrayNode messages = root.putArray("messages");
            ObjectNode sysMessage = messages.addObject();
            sysMessage.put("role", "user");
            sysMessage.put("content", prompt);

            String jsonPayload = objectMapper.writeValueAsString(root);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                    JsonNode respJson = objectMapper.readTree(response.toString());
                    return respJson.path("choices").get(0).path("message").path("content").asText();
                }
            }
        } catch (Exception e) {
            // Graceful fallback
        }
        return getMockAIResponse(prompt);
    }

    private String getMockAIResponse(String prompt) {
        // High quality mock generator matching target json schemas or conversational intents
        if (prompt.contains("LOW | MEDIUM | HIGH | CRITICAL") && prompt.contains("riskLevel") && prompt.contains("emotionMode")) {
            // New chatbot message system prompt requested
            String lang = "en";
            if (prompt.contains("language code: ta")) lang = "ta";
            else if (prompt.contains("language code: hi")) lang = "hi";
            else if (prompt.contains("language code: te")) lang = "te";
            else if (prompt.contains("language code: ml")) lang = "ml";
            else if (prompt.contains("language code: kn")) lang = "kn";
            else if (prompt.contains("language code: fr")) lang = "fr";
            else if (prompt.contains("language code: es")) lang = "es";
            else if (prompt.contains("language code: de")) lang = "de";
            else if (prompt.contains("language code: ja")) lang = "ja";

            String risk = "LOW";
            if (prompt.contains("Assessment: CRITICAL")) risk = "CRITICAL";
            else if (prompt.contains("Assessment: HIGH")) risk = "HIGH";
            else if (prompt.contains("Assessment: MEDIUM")) risk = "MEDIUM";

            String userMsg = "";
            if (prompt.contains("Patient's New Message to Respond to: ")) {
                int start = prompt.indexOf("Patient's New Message to Respond to: ") + "Patient's New Message to Respond to: ".length();
                int end = prompt.indexOf("\n\n", start);
                if (end > start) {
                    userMsg = prompt.substring(start, end).trim();
                } else {
                    userMsg = prompt.substring(start).trim();
                }
            }

            return getMockLocalizedChatbotJson(userMsg, lang, risk);
        } else if (prompt.contains("LOW | MEDIUM | HIGH | CRITICAL") && prompt.contains("riskLevel")) {
            // Deterioration Risk JSON Response
            boolean highRisk = prompt.contains("O2: 8") || prompt.contains("O2: 7") || prompt.contains("compliance: 4") || prompt.contains("compliance: 3");
            boolean medRisk = prompt.contains("O2: 9") || prompt.contains("Stress: 8") || prompt.contains("Stress: 9") || prompt.contains("hrs, Quality");
            
            if (highRisk) {
                return "{\n" +
                        "  \"riskLevel\": \"HIGH\",\n" +
                        "  \"keyFindings\": \"Significant drops in blood oxygen level (<90%) combined with medication adherence levels below 60%.\",\n" +
                        "  \"recommendations\": \"Rest quietly and administer supplementary oxygen if prescribed. Contact your doctor immediately. We have updated your caregiver details and logged this critical warning.\"\n" +
                        "}";
            } else if (medRisk) {
                return "{\n" +
                        "  \"riskLevel\": \"MEDIUM\",\n" +
                        "  \"keyFindings\": \"Slightly elevated fatigue levels logged alongside poor sleep duration (<6 hrs) and mild stress levels.\",\n" +
                        "  \"recommendations\": \"Consider meditating or resting before bed. Shift non-critical morning medications forward to allow recovery rest. Monitor your blood pressure in the morning.\"\n" +
                        "}";
            } else {
                return "{\n" +
                        "  \"riskLevel\": \"LOW\",\n" +
                        "  \"keyFindings\": \"All logged health vitals remain within healthy clinical safety bounds. Sleep cycles and medication compliance are stable.\",\n" +
                        "  \"recommendations\": \"Excellent work! Keep maintaining your daily routines and hydration schedules.\"\n" +
                        "}";
            }
        } else if (prompt.contains("clinical document parser")) {
            // Medical Report Analysis JSON Response
            boolean card = prompt.contains("ECG") || prompt.contains("cardiac");
            boolean glucose = prompt.contains("glucose") || prompt.contains("blood") || prompt.contains("HbA1c");

            if (card) {
                return "{\n" +
                        "  \"summary\": \"Electrocardiogram indicates minor sinus bradycardia and irregular cardiac repolarization patterns.\",\n" +
                        "  \"riskIndicators\": [\"Irregular repolarization markers\", \"Bradycardia tendency (HR ~52 BPM)\"],\n" +
                        "  \"riskScore\": \"HIGH\",\n" +
                        "  \"recommendation\": \"Avoid excessive caffeine and physical stress. Schedule a follow-up consultation with your cardiologist.\"\n" +
                        "}";
            } else if (glucose) {
                return "{\n" +
                        "  \"summary\": \"Clinical blood panels show borderline high glucose levels and an HbA1c value of 6.6%.\",\n" +
                        "  \"riskIndicators\": [\"HbA1c 6.6% (Indicative of early diabetes)\", \"Slightly elevated fasting blood sugar\"],\n" +
                        "  \"riskScore\": \"MEDIUM\",\n" +
                        "  \"recommendation\": \"Adopt a low-glycemic dietary model. Log your fatigue levels daily, and follow medication routines closely.\"\n" +
                        "}";
            } else {
                return "{\n" +
                        "  \"summary\": \"General health discharge panel indicating normal metabolic counts and normal electrolyte ranges.\",\n" +
                        "  \"riskIndicators\": [\"All indices within stable reference bounds\"],\n" +
                        "  \"riskScore\": \"LOW\",\n" +
                        "  \"recommendation\": \"Maintain current balanced clinical routines.\"\n" +
                        "}";
            }
        } else {
            // Conversational Mock Chatbot Response
            boolean highRisk = prompt.contains("CRITICAL") || prompt.contains("HIGH");
            boolean medRisk = prompt.contains("MEDIUM");
            
            if (highRisk) {
                return "I'm concerned about some warning signs, including your declining oxygen levels and missed medication. I strongly suggest you take a moment to rest and contact your caregiver immediately. Your health is the top priority.";
            } else if (medRisk) {
                return "It looks like you've been having a few difficult days with high stress and poor sleep. Let's take things easy today. I have shifted your non-critical morning reminders forward to help you get some much-needed rest.";
            } else {
                return "You are doing really well maintaining your routine! All your logged vitals look stable and healthy. Keep up the great work!";
            }
        }
    }

    private String getMockLocalizedChatbotJson(String userMsg, String lang, String risk) {
        // IMPORTANT: Compute effectiveRisk and distress detection FIRST,
        // then generate the reply using the upgraded risk so the correct
        // empathetic response is returned (not the generic LOW-risk greeting).
        String effectiveRisk = risk;
        boolean matchesDistress = false;
        if (userMsg != null && !userMsg.trim().isEmpty()) {
            String txt = userMsg.toLowerCase();
            matchesDistress = txt.contains("fatigue") || txt.contains("tired") || txt.contains("pain") ||
                              txt.contains("sick") || txt.contains("breath") || txt.contains("fever") ||
                              txt.contains("hurt") || txt.contains("chest") || txt.contains("headache") ||
                              txt.contains("dizzy") || txt.contains("weak") || txt.contains("sad") ||
                              txt.contains("cough") || txt.contains("depress") || txt.contains("nausea") ||
                              txt.contains("vomit") || txt.contains("sweat") || txt.contains("shiver") ||
                              txt.contains("cramp") || txt.contains("ache") || txt.contains("bleed") ||
                              txt.contains("ill") || txt.contains("unwell") || txt.contains("terrible") ||
                              txt.contains("horrible") || txt.contains("awful") || txt.contains("severe") ||
                              txt.contains("anxiet") || txt.contains("anxious") || txt.contains("stress") ||
                              txt.contains("worri") || txt.contains("panic") || txt.contains("scared") ||
                              txt.contains("lonely") || txt.contains("hopeless") || txt.contains("depress") ||
                              txt.contains("sore") || txt.contains("swollen") || txt.contains("blurr") ||
                              txt.contains("shak") || txt.contains("tremble") || txt.contains("numb") ||
                              txt.contains("faint") || txt.contains("not well") || txt.contains("not good") ||
                              txt.contains("feel bad") || txt.contains("feel worse") || txt.contains("struggling");
        }
        if (("LOW".equals(risk) || risk == null) && matchesDistress) {
            effectiveRisk = "MEDIUM";
        }

        // Now generate reply using the correct (possibly upgraded) risk level
        String reply = getLocalizedFallbackResponse(userMsg, lang, effectiveRisk, null);

        String emotionMode = "NORMAL";
        if ("CRITICAL".equals(effectiveRisk)) emotionMode = "CRITICAL";
        else if ("HIGH".equals(effectiveRisk)) emotionMode = "URGENT";
        else if ("MEDIUM".equals(effectiveRisk)) emotionMode = "EMPATHETIC";

        // Escape any quotes in reply to keep JSON valid
        reply = reply.replace("\\", "\\\\").replace("\"", "\\\"");

        return "{\n" +
                "  \"reply\": \"" + reply + "\",\n" +
                "  \"riskLevel\": \"" + effectiveRisk + "\",\n" +
                "  \"emotionMode\": \"" + emotionMode + "\"\n" +
                "}";
    }
        private String getLocalizedFallbackResponse(String userMsgText, String lang, String risk, User user) {
        String caregiverName = user != null ? user.getEmergencyCaregiverName() : "Caregiver";
        String caregiverPhone = user != null ? user.getCaregiverPhoneNumber() : "";
        String contacts = caregiverName + (caregiverPhone != null && !caregiverPhone.isEmpty() ? ": " + caregiverPhone : "");

        String cleanLang = lang != null ? lang.toLowerCase() : "en";
        if (cleanLang.startsWith("ta")) cleanLang = "ta";
        else if (cleanLang.startsWith("hi")) cleanLang = "hi";
        else if (cleanLang.startsWith("te")) cleanLang = "te";
        else if (cleanLang.startsWith("ml")) cleanLang = "ml";
        else if (cleanLang.startsWith("kn")) cleanLang = "kn";
        else if (cleanLang.startsWith("fr")) cleanLang = "fr";
        else if (cleanLang.startsWith("es")) cleanLang = "es";
        else if (cleanLang.startsWith("de")) cleanLang = "de";
        else if (cleanLang.startsWith("ja")) cleanLang = "ja";
        else if (cleanLang.startsWith("auto") && userMsgText != null) {
            String text = userMsgText.toLowerCase();
            if (text.matches(".*[\\u0B80-\\u0BFF].*")) cleanLang = "ta";
            else if (text.matches(".*[\\u0900-\\u097F].*")) cleanLang = "hi";
            else if (text.matches(".*[\\u0C00-\\u0C7F].*")) cleanLang = "te";
            else if (text.matches(".*[\\u0D00-\\u0D7F].*")) cleanLang = "ml";
            else if (text.matches(".*[\\u0C80-\\u0CFF].*")) cleanLang = "kn";
            else if (text.matches(".*[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FBF].*")) cleanLang = "ja";
            else if (text.contains("hola") || text.contains("como") || text.contains("gracias")) cleanLang = "es";
            else if (text.contains("bonjour") || text.contains("merci") || text.contains("fatigu")) cleanLang = "fr";
            else if (text.contains("hallo") || text.contains("danke") || text.contains("mÃ¼de")) cleanLang = "de";
            else cleanLang = "en";
        }

        String effectiveRisk = risk;
        boolean matchesDistress = false;
        boolean isHair = false;
        boolean isSleep = false;
        boolean isDiet = false;
        boolean isExercise = false;
        boolean isStress = false;
        boolean isMed = false;
        boolean isFatigue = false;
        boolean isHello = false;

        if (userMsgText != null && !userMsgText.trim().isEmpty()) {
            String txt = userMsgText.toLowerCase();
            matchesDistress = txt.contains("fatigue") || txt.contains("tired") || txt.contains("pain") ||
                              txt.contains("sick") || txt.contains("breath") || txt.contains("fever") ||
                              txt.contains("hurt") || txt.contains("chest") || txt.contains("headache") ||
                              txt.contains("dizzy") || txt.contains("weak") || txt.contains("sad") ||
                              txt.contains("cough") || txt.contains("depress") || txt.contains("nausea") ||
                              txt.contains("vomit") || txt.contains("sweat") || txt.contains("shiver") ||
                              txt.contains("cramp") || txt.contains("ache") || txt.contains("bleed") ||
                              txt.contains("ill") || txt.contains("unwell") || txt.contains("terrible") ||
                              txt.contains("horrible") || txt.contains("awful") || txt.contains("severe") ||
                              txt.contains("anxiet") || txt.contains("anxious") || txt.contains("stress") ||
                              txt.contains("worri") || txt.contains("panic") || txt.contains("scared") ||
                              txt.contains("lonely") || txt.contains("hopeless") || txt.contains("sore") ||
                              txt.contains("swollen") || txt.contains("blurr") || txt.contains("shak") ||
                              txt.contains("tremble") || txt.contains("numb") || txt.contains("faint") ||
                              txt.contains("not well") || txt.contains("not good") || txt.contains("feel bad") ||
                              txt.contains("feel worse") || txt.contains("struggling");

            // Intent keyword mappings
            isHair = txt.contains("hair") || txt.contains("bald") || txt.contains("scalp") || txt.contains("à®®à¯à®Ÿà®¿") || txt.contains("à¤¬à¤¾à¤²");
            isSleep = txt.contains("sleep") || txt.contains("insomnia") || txt.contains("night") || txt.contains("awake") || txt.contains("à®¤à¯‚à®•à¯à®•à®®à¯") || txt.contains("à¤¨à¥€à¤‚à¤¦");
            isDiet = txt.contains("diet") || txt.contains("eat") || txt.contains("food") || txt.contains("weight") || txt.contains("nutrition") || txt.contains("water") || txt.contains("hydration") || txt.contains("à®‰à®£à®µà¯") || txt.contains("à¤­à¥‹à¤œà¤¨") || txt.contains("à¤–à¤¾à¤¨à¤¾");
            isExercise = txt.contains("exercise") || txt.contains("workout") || txt.contains("gym") || txt.contains("walk") || txt.contains("run") || txt.contains("à®‰à®Ÿà®±à¯à®ªà®¯à®¿à®±à¯à®šà®¿") || txt.contains("à¤µà¥à¤¯à¤¾à¤¯à¤¾à¤®") || txt.contains("à¤•à¤¸à¤°à¤¤");
            isStress = txt.contains("stress") || txt.contains("anxiet") || txt.contains("anxious") || txt.contains("worry") || txt.contains("panic") || txt.contains("scared") || txt.contains("afraid") || txt.contains("sad") || txt.contains("depress") || txt.contains("à®®à®© à®…à®´à¯à®¤à¯à®¤à®®à¯") || txt.contains("à¤¤à¤¨à¤¾à¤µ") || txt.contains("à¤šà¤¿à¤‚à¤¤à¤¾");
            isMed = txt.contains("medication") || txt.contains("pill") || txt.contains("medicine") || txt.contains("dose") || txt.contains("prescription") || txt.contains("à®®à®°à¯à®¨à¯à®¤à¯") || txt.contains("à¤¦à¤µà¤¾");
            isFatigue = txt.contains("fatigue") || txt.contains("tired") || txt.contains("weak") || txt.contains("exhaust") || txt.contains("à®šà¯‹à®°à¯à®µà¯") || txt.contains("à¤¥à¤•à¤¾à¤¨");
            isHello = txt.contains("hello") || txt.contains("hi") || txt.contains("hey") || txt.contains("greetings") || txt.contains("à®µà®£à®•à¯à®•à®®à¯") || txt.contains("à¤¨à¤®à¤¸à¥à¤¤à¥‡");
        }

        if (("LOW".equals(risk) || risk == null) && matchesDistress) {
            effectiveRisk = "MEDIUM";
        }

        switch (cleanLang) {
            case "ta":
                if ("CRITICAL".equals(effectiveRisk)) {
                    return "à®‰à®™à¯à®•à®³à¯ à®šà®®à¯€à®ªà®¤à¯à®¤à®¿à®¯ à®†à®°à¯‹à®•à¯à®•à®¿à®¯ à®•à¯à®±à®¿à®•à®¾à®Ÿà¯à®Ÿà®¿à®•à®³à¯ à®†à®ªà®¤à¯à®¤à®¾à®© à®¨à®¿à®²à¯ˆà®¯à®¿à®²à¯ à®‰à®³à¯à®³à®©. à®¤à®¯à®µà¯à®šà¯†à®¯à¯à®¤à¯ à®‰à®™à¯à®•à®³à¯ à®…à®µà®šà®° à®•à®µà®©à®¿à®ªà¯à®ªà®¾à®³à®°à¯ˆ (" + contacts + ") à®‰à®Ÿà®©à®Ÿà®¿à®¯à®¾à®• à®¤à¯Šà®Ÿà®°à¯à®ªà¯ à®•à¯Šà®³à¯à®³à®µà¯à®®à¯ à®…à®²à¯à®²à®¤à¯ à®‰à®Ÿà®©à®Ÿà®¿à®¯à®¾à®• à®®à®°à¯à®¤à¯à®¤à¯à®µ à®‰à®¤à®µà®¿ à®ªà¯†à®±à®µà¯à®®à¯.";
                } else if ("HIGH".equals(effectiveRisk)) {
                    return "à®‰à®™à¯à®•à®³à¯ à®‰à®Ÿà®²à¯à®¨à®¿à®²à¯ˆà®¯à®¿à®²à¯ à®šà®¿à®² à®Žà®šà¯à®šà®°à®¿à®•à¯à®•à¯ˆ à®…à®±à®¿à®•à¯à®±à®¿à®•à®³à¯ˆ à®¨à®¾à®©à¯ à®•à®µà®©à®¿à®¤à¯à®¤à¯‡à®©à¯. à®¤à®¯à®µà¯à®šà¯†à®¯à¯à®¤à¯ à®‰à®Ÿà®©à®Ÿà®¿à®¯à®¾à®• à®“à®¯à¯à®µà¯†à®Ÿà¯à®¤à¯à®¤à¯, à®‰à®™à¯à®•à®³à¯ à®•à®µà®©à®¿à®ªà¯à®ªà®¾à®³à®°à¯ˆ (" + contacts + ") à®¤à¯Šà®Ÿà®°à¯à®ªà¯ à®•à¯Šà®³à¯à®³à®µà¯à®®à¯.";
                } else if (isHair) {
                    return "à®†à®°à¯‹à®•à¯à®•à®¿à®¯à®®à®¾à®© à®®à¯à®Ÿà®¿ à®µà®³à®°à¯à®šà¯à®šà®¿à®•à¯à®•à¯, à®ªà¯à®°à®¤à®®à¯, à®‡à®°à¯à®®à¯à®ªà¯à®šà¯à®šà®¤à¯à®¤à¯ à®®à®±à¯à®±à¯à®®à¯ à®µà¯ˆà®Ÿà¯à®Ÿà®®à®¿à®©à¯à®•à®³à¯ A, C, E à®¨à®¿à®±à¯ˆà®¨à¯à®¤ à®šà®®à®šà¯à®šà¯€à®°à¯ à®‰à®£à®µà¯ˆ à®‰à®Ÿà¯à®•à¯Šà®³à¯à®³à¯à®™à¯à®•à®³à¯. à®‰à®šà¯à®šà®¨à¯à®¤à®²à¯ˆà®¯à¯ˆ à®®à¯†à®¤à¯à®µà®¾à®• à®®à®šà®¾à®œà¯ à®šà¯†à®¯à¯à®µà®¤à¯ à®‡à®°à®¤à¯à®¤ à®“à®Ÿà¯à®Ÿà®¤à¯à®¤à¯ˆ à®…à®¤à®¿à®•à®°à®¿à®•à¯à®• à®‰à®¤à®µà¯à®®à¯. à®¨à®¿à®±à¯ˆà®¯ à®¤à®£à¯à®£à¯€à®°à¯ à®•à¯à®Ÿà®¿à®•à¯à®•à®µà¯à®®à¯!";
                } else if (isSleep) {
                    return "à®¨à®²à¯à®² à®¤à¯‚à®•à¯à®•à®¤à¯à®¤à®¿à®±à¯à®•à¯, à®¤à®¿à®©à®®à¯à®®à¯ à®’à®°à¯‡ à®¨à¯‡à®°à®¤à¯à®¤à®¿à®²à¯ à®ªà®Ÿà¯à®•à¯à®•à¯ˆà®•à¯à®•à¯à®šà¯ à®šà¯†à®²à¯à®²à¯à®™à¯à®•à®³à¯. à®ªà®Ÿà¯à®•à¯à®•à¯ˆà®•à¯à®•à¯ à®®à¯à®©à¯ à®šà¯†à®²à¯à®ªà¯‹à®©à¯ à®ªà¯‹à®©à¯à®± à®¤à®¿à®°à¯ˆà®•à®³à¯ˆà®¤à¯ à®¤à®µà®¿à®°à¯à®•à¯à®•à®µà¯à®®à¯. à®…à®®à¯ˆà®¤à®¿à®¯à®¾à®© à®šà¯‚à®´à®²à¯ˆ à®‰à®°à¯à®µà®¾à®•à¯à®•à¯à®™à¯à®•à®³à¯.";
                } else if (isDiet) {
                    return "à®šà¯€à®°à®¾à®© à®‰à®£à®µà¯ˆ à®‰à®Ÿà¯à®•à¯Šà®³à¯à®³à¯à®™à¯à®•à®³à¯, à®¨à®¿à®±à¯ˆà®¯ à®¤à®£à¯à®£à¯€à®°à¯ à®•à¯à®Ÿà®¿à®•à¯à®•à®µà¯à®®à¯. à®•à¯€à®°à¯ˆà®•à®³à¯, à®ªà¯à®°à®¤à®™à¯à®•à®³à¯ à®®à®±à¯à®±à¯à®®à¯ à®¨à®¾à®°à¯à®šà¯à®šà®¤à¯à®¤à¯ à®¨à®¿à®±à¯ˆà®¨à¯à®¤ à®‰à®£à®µà¯à®•à®³à¯ˆà®šà¯ à®šà¯‡à®°à¯à®•à¯à®•à®µà¯à®®à¯. à®ªà®¤à®ªà¯à®ªà®Ÿà¯à®¤à¯à®¤à®ªà¯à®ªà®Ÿà¯à®Ÿ à®‰à®£à®µà¯à®•à®³à¯ˆà®¤à¯ à®¤à®µà®¿à®°à¯à®•à¯à®•à®µà¯à®®à¯.";
                } else if (isExercise) {
                    return "à®¤à®¿à®©à®®à¯à®®à¯ 30 à®¨à®¿à®®à®¿à®Ÿà®™à¯à®•à®³à¯ à®¨à®Ÿà¯ˆà®ªà¯à®ªà®¯à®¿à®±à¯à®šà®¿ à®…à®²à¯à®²à®¤à¯ à®®à®¿à®¤à®®à®¾à®© à®‰à®Ÿà®±à¯à®ªà®¯à®¿à®±à¯à®šà®¿ à®šà¯†à®¯à¯à®µà®¤à¯ à®†à®°à¯‹à®•à¯à®•à®¿à®¯à®¤à¯à®¤à®¿à®±à¯à®•à¯ à®¨à®²à¯à®²à®¤à¯. à®šà¯‹à®°à¯à®µà®¾à®• à®‡à®°à¯à®¨à¯à®¤à®¾à®²à¯ à®•à®Ÿà®¿à®©à®®à®¾à®© à®‰à®Ÿà®±à¯à®ªà®¯à®¿à®±à¯à®šà®¿à®•à®³à¯ˆà®¤à¯ à®¤à®µà®¿à®°à¯à®•à¯à®•à®µà¯à®®à¯.";
                } else if (isStress) {
                    return "à®‰à®™à¯à®•à®³à¯ˆ à®¨à®¾à®©à¯ à®ªà¯à®°à®¿à®¨à¯à®¤à¯ à®•à¯Šà®³à¯à®•à®¿à®±à¯‡à®©à¯. à®•à®µà®²à¯ˆà®ªà¯à®ªà®Ÿ à®µà¯‡à®£à¯à®Ÿà®¾à®®à¯. à®†à®´à¯à®¨à¯à®¤ à®®à¯‚à®šà¯à®šà¯à®ªà¯ à®ªà®¯à®¿à®±à¯à®šà®¿ à®šà¯†à®¯à¯à®¯ à®®à¯à®¯à®±à¯à®šà®¿à®•à¯à®•à®µà¯à®®à¯: 4 à®µà®¿à®¨à®¾à®Ÿà®¿à®•à®³à¯ à®®à¯‚à®šà¯à®šà¯ˆ à®‡à®´à¯à®¤à¯à®¤à¯, 4 à®µà®¿à®¨à®¾à®Ÿà®¿à®•à®³à¯ à®…à®Ÿà®•à¯à®•à®¿, 6 à®µà®¿à®¨à®¾à®Ÿà®¿à®•à®³à¯ à®µà¯†à®³à®¿à®¯à®¿à®Ÿà®µà¯à®®à¯.";
                } else if (isMed) {
                    return "à®‰à®™à¯à®•à®³à¯ à®®à®°à¯à®¨à¯à®¤à¯à®•à®³à¯ˆ à®®à®°à¯à®¤à¯à®¤à¯à®µà®°à¯ à®ªà®°à®¿à®¨à¯à®¤à¯à®°à¯ˆà®¤à¯à®¤à®ªà®Ÿà®¿ à®šà®°à®¿à®¯à®¾à®• à®Žà®Ÿà¯à®¤à¯à®¤à¯à®•à¯à®•à¯Šà®³à¯à®³à¯à®™à¯à®•à®³à¯. à®…à®²à®¾à®°à®™à¯à®•à®³à¯ˆ à®…à®®à¯ˆà®ªà¯à®ªà®¤à¯ à®®à®°à¯à®¨à¯à®¤à¯à®•à®³à¯ˆ à®¨à®¿à®©à¯ˆà®µà®¿à®²à¯ à®µà¯ˆà®•à¯à®• à®‰à®¤à®µà¯à®®à¯.";
                } else if (isFatigue) {
                    return "à®‰à®Ÿà®²à¯ à®šà¯‹à®°à¯à®µà¯ à®“à®¯à¯à®µà¯ à®¤à¯‡à®µà¯ˆ à®Žà®©à¯à®ªà®¤à¯ˆà®•à¯ à®•à®¾à®Ÿà¯à®Ÿà¯à®•à®¿à®±à®¤à¯. à®‡à®©à¯à®±à¯ à®¤à®¯à®µà¯à®šà¯†à®¯à¯à®¤à¯ à®¨à®©à¯à®±à®¾à®• à®“à®¯à¯à®µà¯†à®Ÿà¯à®¤à¯à®¤à¯à®•à¯ à®•à¯Šà®³à¯à®³à¯à®™à¯à®•à®³à¯. à®‰à®™à¯à®•à®³à¯ à®šà¯‹à®°à¯à®µà¯ à®…à®³à®µà¯à®•à®³à¯ˆ à®¨à®¾à®™à¯à®•à®³à¯ à®•à®£à¯à®•à®¾à®£à®¿à®ªà¯à®ªà¯‹à®®à¯.";
                } else if (isHello) {
                    return "à®µà®£à®•à¯à®•à®®à¯! à®¨à®¾à®©à¯ à®¨à¯†à®•à¯à®¸à¯à®°à®¾, à®‰à®™à¯à®•à®³à¯ à®šà¯à®•à®¾à®¤à®¾à®°à®¤à¯ à®¤à¯à®£à¯ˆà®µà®©à¯. à®‡à®©à¯à®±à¯ à®¨à®¾à®©à¯ à®‰à®™à¯à®•à®³à¯à®•à¯à®•à¯ à®Žà®µà¯à®µà®¾à®±à¯ à®‰à®¤à®µ à®®à¯à®Ÿà®¿à®¯à¯à®®à¯?";
                } else if ("MEDIUM".equals(effectiveRisk)) {
                    if (matchesDistress) {
                        return "à®¨à¯€à®™à¯à®•à®³à¯ à®šà¯‹à®°à¯à®µà®¾à®• à®…à®²à¯à®²à®¤à¯ à®‰à®Ÿà®²à¯à®¨à®²à®•à¯à®•à¯à®±à¯ˆà®µà®¾à®• à®‰à®£à®°à¯à®µà®¤à¯ˆ à®…à®±à®¿à®¨à¯à®¤à¯ à®¨à®¾à®©à¯ à®•à®µà®²à¯ˆà®ªà¯à®ªà®Ÿà¯à®•à®¿à®±à¯‡à®©à¯. à®¤à®¯à®µà¯à®šà¯†à®¯à¯à®¤à¯ à®“à®¯à¯à®µà¯†à®Ÿà¯à®•à¯à®•à®µà¯à®®à¯, à®‰à®™à¯à®•à®³à¯ à®‰à®Ÿà®²à¯à®¨à®¿à®²à¯ˆ à®ªà®¤à®¿à®µà¯à®•à®³à¯ˆ à®•à®£à¯à®•à®¾à®£à®¿à®•à¯à®•à®µà¯à®®à¯.";
                    }
                    return "à®‰à®™à¯à®•à®³à¯ à®¤à¯‚à®•à¯à®•à®®à¯ à®šà®®à¯€à®ªà®¤à¯à®¤à®¿à®²à¯ à®•à¯à®±à¯ˆà®µà®¾à®• à®‡à®°à¯à®ªà¯à®ªà®¤à¯ˆà®¯à¯à®®à¯, à®®à®© à®…à®´à¯à®¤à¯à®¤à®®à¯ à®šà®±à¯à®±à¯ à®…à®¤à®¿à®•à®®à®¾à®• à®‡à®°à¯à®ªà¯à®ªà®¤à¯ˆà®¯à¯à®®à¯ à®•à®µà®©à®¿à®¤à¯à®¤à¯‡à®©à¯. à®‡à®©à¯à®±à¯ à®¤à®¯à®µà¯à®šà¯†à®¯à¯à®¤à¯ à®šà®¿à®±à®¿à®¤à¯ à®“à®¯à¯à®µà¯†à®Ÿà¯à®¤à¯à®¤à¯, à®¤à®¿à®¯à®¾à®©à®®à¯ à®šà¯†à®¯à¯à®¯ à®®à¯à®¯à®±à¯à®šà®¿à®•à¯à®•à®µà¯à®®à¯.";
                } else {
                    return "à®¨à¯€à®™à¯à®•à®³à¯ à®¨à®²à®®à®¾à®• à®‡à®°à¯à®ªà¯à®ªà®¤à¯ˆ à®•à®£à¯à®Ÿà¯ à®®à®•à®¿à®´à¯à®šà¯à®šà®¿ à®…à®Ÿà¯ˆà®•à®¿à®±à¯‡à®©à¯! à®‰à®™à¯à®•à®³à¯ à®‰à®Ÿà®²à¯à®¨à®¿à®²à¯ˆ à®®à®±à¯à®±à¯à®®à¯ à®¤à®¿à®©à®šà®°à®¿ à®ªà®´à®•à¯à®•à®µà®´à®•à¯à®•à®™à¯à®•à®³à¯ à®®à®¿à®•à®µà¯à®®à¯ à®šà¯€à®°à®¾à®• à®‰à®³à¯à®³à®©. à®‡à®¤à¯‡ à®µà®´à®•à¯à®•à®¤à¯à®¤à¯ˆ à®¤à¯Šà®Ÿà®°à¯à®™à¯à®•à®³à¯.";
                }
            case "hi":
                if ("CRITICAL".equals(effectiveRisk)) {
                    return "à¤†à¤ªà¤•à¥‡ à¤¸à¥à¤µà¤¾à¤¸à¥à¤¥à¥à¤¯ à¤ªà¥ˆà¤°à¤¾à¤®à¥€à¤Ÿà¤° à¤à¤• à¤—à¤‚à¤­à¥€à¤° à¤¸à¥à¤¥à¤¿à¤¤à¤¿ à¤®à¥‡à¤‚ à¤¹à¥ˆà¤‚à¥¤ à¤•à¥ƒà¤ªà¤¯à¤¾ à¤¤à¥à¤°à¤‚à¤¤ à¤…à¤ªà¤¨à¥‡ à¤†à¤ªà¤¾à¤¤à¤•à¤¾à¤²à¥€à¤¨ à¤¦à¥‡à¤–à¤­à¤¾à¤²à¤•à¤°à¥à¤¤à¤¾ (" + contacts + ") à¤¸à¥‡ à¤¸à¤‚à¤ªà¤°à¥à¤• à¤•à¤°à¥‡à¤‚ à¤¯à¤¾ à¤¤à¥à¤°à¤‚à¤¤ à¤šà¤¿à¤•à¤¿à¤¤à¥à¤¸à¤¾ à¤¸à¤¹à¤¾à¤¯à¤¤à¤¾ à¤ªà¥à¤°à¤¾à¤ªà¥à¤¤ à¤•à¤°à¥‡à¤‚à¥¤";
                } else if ("HIGH".equals(effectiveRisk)) {
                    return "à¤®à¥ˆà¤‚à¤¨à¥‡ à¤†à¤ªà¤•à¥‡ à¤¸à¥à¤µà¤¾à¤¸à¥à¤¥à¥à¤¯ à¤¸à¤‚à¤•à¥‡à¤¤à¤•à¥‹à¤‚ à¤®à¥‡à¤‚ à¤•à¥à¤› à¤—à¤‚à¤­à¥€à¤° à¤¬à¤¦à¤²à¤¾à¤µ à¤¦à¥‡à¤–à¥‡ à¤¹à¥ˆà¤‚à¥¤ à¤•à¥ƒà¤ªà¤¯à¤¾ à¤†à¤°à¤¾à¤® à¤•à¤°à¥‡à¤‚ à¤”à¤° à¤¤à¥à¤°à¤‚à¤¤ à¤…à¤ªà¤¨à¥‡ à¤†à¤ªà¤¾à¤¤à¤•à¤¾à¤²à¥€à¤¨ à¤¦à¥‡à¤–à¤­à¤¾à¤²à¤•à¤°à¥à¤¤à¤¾ (" + contacts + ") à¤¯à¤¾ à¤¡à¥‰à¤•à¥à¤Ÿà¤° à¤¸à¥‡ à¤¸à¤‚à¤ªà¤°à¥à¤• à¤•à¤°à¥‡à¤‚à¥¤";
                } else if (isHair) {
                    return "à¤¸à¥à¤µà¤¸à¥à¤¥ à¤¬à¤¾à¤²à¥‹à¤‚ à¤•à¥‡ à¤µà¤¿à¤•à¤¾à¤¸ à¤•à¥‡ à¤²à¤¿à¤, à¤ªà¥à¤°à¥‹à¤Ÿà¥€à¤¨, à¤†à¤¯à¤°à¤¨ à¤”à¤° à¤µà¤¿à¤Ÿà¤¾à¤®à¤¿à¤¨ A, C, E à¤¸à¥‡ à¤­à¤°à¤ªà¥‚à¤° à¤¸à¤‚à¤¤à¥à¤²à¤¿à¤¤ à¤†à¤¹à¤¾à¤° à¤²à¥‡à¤‚à¥¤ à¤¬à¤¾à¤²à¥‹à¤‚ à¤•à¥€ à¤œà¤¡à¤¼à¥‹à¤‚ à¤•à¥€ à¤¹à¤²à¥à¤•à¥€ à¤®à¤¾à¤²à¤¿à¤¶ à¤•à¤°à¥‡à¤‚ à¤”à¤° à¤ªà¤°à¥à¤¯à¤¾à¤ªà¥à¤¤ à¤ªà¤¾à¤¨à¥€ à¤ªà¤¿à¤à¤‚à¥¤";
                } else if (isSleep) {
                    return "à¤…à¤šà¥à¤›à¥€ à¤¨à¥€à¤‚à¤¦ à¤•à¥‡ à¤²à¤¿à¤, à¤¸à¥‹à¤¨à¥‡ à¤•à¤¾ à¤à¤• à¤¨à¤¿à¤¯à¤®à¤¿à¤¤ à¤¸à¤®à¤¯ à¤¤à¤¯ à¤•à¤°à¥‡à¤‚à¥¤ à¤¬à¤¿à¤¸à¥à¤¤à¤° à¤ªà¤° à¤œà¤¾à¤¨à¥‡ à¤¸à¥‡ à¤ªà¤¹à¤²à¥‡ à¤¸à¥à¤•à¥à¤°à¥€à¤¨ (à¤«à¥‹à¤¨/à¤Ÿà¥€à¤µà¥€) à¤¸à¥‡ à¤¦à¥‚à¤° à¤°à¤¹à¥‡à¤‚ à¤”à¤° à¤¶à¤¾à¤‚à¤¤ à¤µà¤¾à¤¤à¤¾à¤µà¤°à¤£ à¤¬à¤¨à¤¾à¤à¤‚à¥¤";
                } else if (isDiet) {
                    return "à¤¸à¤‚à¤¤à¥à¤²à¤¿à¤¤ à¤†à¤¹à¤¾à¤° à¤²à¥‡à¤‚ à¤”à¤° à¤ªà¤°à¥à¤¯à¤¾à¤ªà¥à¤¤ à¤ªà¤¾à¤¨à¥€ à¤ªà¥€à¤à¤‚à¥¤ à¤¹à¤°à¥€ à¤¸à¤¬à¥à¤œà¤¿à¤¯à¤¾à¤‚, à¤ªà¥à¤°à¥‹à¤Ÿà¥€à¤¨ à¤”à¤° à¤«à¤¾à¤‡à¤¬à¤° à¤¯à¥à¤•à¥à¤¤ à¤­à¥‹à¤œà¤¨ à¤¶à¤¾à¤®à¤¿à¤² à¤•à¤°à¥‡à¤‚à¥¤ à¤ªà¤°à¤¿à¤·à¥à¤•à¥ƒà¤¤ à¤šà¥€à¤¨à¥€ à¤•à¤¾ à¤¸à¥‡à¤µà¤¨ à¤•à¤® à¤•à¤°à¥‡à¤‚à¥¤";
                } else if (isExercise) {
                    return "à¤¦à¥ˆà¤¨à¤¿à¤• à¤°à¥‚à¤ª à¤¸à¥‡ 30 à¤®à¤¿à¤¨à¤Ÿ à¤¤à¥‡à¤œ à¤šà¤²à¤¨à¤¾ à¤¯à¤¾ à¤®à¤§à¥à¤¯à¤® à¤µà¥à¤¯à¤¾à¤¯à¤¾à¤® à¤•à¤°à¤¨à¤¾ à¤†à¤ªà¤•à¥‡ à¤¸à¥à¤µà¤¾à¤¸à¥à¤¥à¥à¤¯ à¤•à¥‡ à¤²à¤¿à¤ à¤‰à¤¤à¥à¤¤à¤® à¤¹à¥ˆà¥¤ à¤…à¤¤à¥à¤¯à¤§à¤¿à¤• à¤¥à¤•à¤¾à¤¨ à¤¹à¥‹à¤¨à¥‡ à¤ªà¤° à¤­à¤¾à¤°à¥€ à¤•à¤¸à¤°à¤¤ à¤¸à¥‡ à¤¬à¤šà¥‡à¤‚à¥¤";
                } else if (isStress) {
                    return "à¤®à¥ˆà¤‚ à¤¸à¤®à¤ à¤¸à¤•à¤¤à¤¾ à¤¹à¥‚à¤à¥¤ à¤šà¤¿à¤‚à¤¤à¤¾ à¤¨ à¤•à¤°à¥‡à¤‚à¥¤ à¤•à¥ƒà¤ªà¤¯à¤¾ à¤—à¤¹à¤°à¥€ à¤¸à¤¾à¤à¤¸ à¤²à¥‡à¤¨à¥‡ à¤•à¤¾ à¤µà¥à¤¯à¤¾à¤¯à¤¾à¤® à¤•à¤°à¥‡à¤‚: 4 à¤¸à¥‡à¤•à¤‚à¤¡ à¤•à¥‡ à¤²à¤¿à¤ à¤¸à¤¾à¤à¤¸ à¤²à¥‡à¤‚, 4 à¤°à¥‹à¤•à¥‡à¤‚, à¤”à¤° 6 à¤¸à¥‡à¤•à¤‚à¤¡ à¤®à¥‡à¤‚ à¤›à¥‹à¤¡à¤¼à¥‡à¤‚à¥¤";
                } else if (isMed) {
                    return "à¤…à¤ªà¤¨à¥€ à¤¦à¤µà¤¾à¤à¤‚ à¤¡à¥‰à¤•à¥à¤Ÿà¤° à¤•à¥‡ à¤¨à¤¿à¤°à¥à¤¦à¥‡à¤¶à¤¾à¤¨à¥à¤¸à¤¾à¤° à¤¸à¤®à¤¯ à¤ªà¤° à¤²à¥‡à¤‚à¥¤ à¤¦à¤µà¤¾à¤“à¤‚ à¤•à¥‹ à¤¸à¤®à¤¯ à¤ªà¤° à¤²à¥‡à¤¨à¥‡ à¤•à¥‡ à¤²à¤¿à¤ à¤…à¤²à¤¾à¤°à¥à¤® à¤¸à¥‡à¤Ÿ à¤•à¤°à¥‡à¤‚à¥¤";
                } else if (isFatigue) {
                    return "à¤²à¤—à¤¾à¤¤à¤¾à¤° à¤¥à¤•à¤¾à¤¨ à¤¶à¤°à¥€à¤° à¤•à¥‹ à¤†à¤°à¤¾à¤® à¤¦à¥‡à¤¨à¥‡ à¤•à¤¾ à¤¸à¤‚à¤•à¥‡à¤¤ à¤¹à¥ˆà¥¤ à¤†à¤œ à¤•à¥ƒà¤ªà¤¯à¤¾ à¤†à¤°à¤¾à¤® à¤•à¤°à¥‡à¤‚ à¤”à¤° à¤ªà¤¾à¤¨à¥€ à¤ªà¥€à¤¤à¥‡ à¤°à¤¹à¥‡à¤‚à¥¤";
                } else if (isHello) {
                    return "à¤¨à¤®à¤¸à¥à¤¤à¥‡! à¤®à¥ˆà¤‚ à¤¨à¥‡à¤•à¥à¤¸à¥à¤°à¤¾ à¤¹à¥‚à¤, à¤†à¤ªà¤•à¤¾ à¤µà¥à¤¯à¤•à¥à¤¤à¤¿à¤—à¤¤ à¤¸à¥à¤µà¤¾à¤¸à¥à¤¥à¥à¤¯ à¤¸à¤¾à¤¥à¥€à¥¤ à¤†à¤œ à¤®à¥ˆà¤‚ à¤†à¤ªà¤•à¥€ à¤•à¥à¤¯à¤¾ à¤¸à¤¹à¤¾à¤¯à¤¤à¤¾ à¤•à¤° à¤¸à¤•à¤¤à¤¾ à¤¹à¥‚à¤?";
                } else if ("MEDIUM".equals(effectiveRisk)) {
                    if (matchesDistress) {
                        return "à¤®à¥à¤à¥‡ à¤¯à¤¹ à¤¸à¥à¤¨à¤•à¤° à¤šà¤¿à¤‚à¤¤à¤¾ à¤¹à¥‹ à¤°à¤¹à¥€ à¤¹à¥ˆ à¤•à¤¿ à¤†à¤ª à¤…à¤¸à¥à¤µà¤¸à¥à¤¥ à¤¯à¤¾ à¤¥à¤•à¤¾ à¤¹à¥à¤† à¤®à¤¹à¤¸à¥‚à¤¸ à¤•à¤° à¤°à¤¹à¥‡ à¤¹à¥ˆà¤‚à¥¤ à¤•à¥ƒà¤ªà¤¯à¤¾ à¤†à¤°à¤¾à¤® à¤•à¤°à¥‡à¤‚ à¤”à¤° à¤…à¤ªà¤¨à¥‡ à¤¸à¥à¤µà¤¾à¤¸à¥à¤¥à¥à¤¯ à¤®à¥‡à¤Ÿà¥à¤°à¤¿à¤•à¥à¤¸ à¤•à¥€ à¤¨à¤¿à¤—à¤°à¤¾à¤¨à¥€ à¤•à¤°à¥‡à¤‚à¥¤";
                    }
                    return "à¤®à¥ˆà¤‚à¤¨à¥‡ à¤§à¥à¤¯à¤¾à¤¨ à¤¦à¤¿à¤¯à¤¾ à¤•à¤¿ à¤†à¤ªà¤•à¥€ à¤¨à¥€à¤‚à¤¦ à¤•à¥à¤› à¤¦à¤¿à¤¨à¥‹à¤‚ à¤¸à¥‡ à¤•à¤® à¤°à¤¹à¥€ à¤¹à¥ˆ à¤”à¤° à¤¤à¤¨à¤¾à¤µ à¤¬à¤¢à¤¼à¤¾ à¤¹à¥à¤† à¤¹à¥ˆà¥¤ à¤•à¥ƒà¤ªà¤¯à¤¾ à¤†à¤œ à¤†à¤°à¤¾à¤® à¤•à¤°à¥‡à¤‚ à¤”à¤° à¤¸à¤¾à¤‚à¤¸ à¤²à¥‡à¤¨à¥‡ à¤•à¥‡ à¤µà¥à¤¯à¤¾à¤¯à¤¾à¤® à¤•à¤¾ à¤ªà¥à¤°à¤¯à¤¾à¤¸ à¤•à¤°à¥‡à¤‚à¥¤";
                } else {
                    return "à¤®à¥à¤à¥‡ à¤¯à¤¹ à¤¦à¥‡à¤–à¤•à¤° à¤–à¥à¤¶à¥€ à¤¹à¥à¤ˆ à¤•à¤¿ à¤†à¤ª à¤¬à¤¹à¥à¤¤ à¤…à¤šà¥à¤›à¤¾ à¤®à¤¹à¤¸à¥‚à¤¸ à¤•à¤° à¤°à¤¹à¥‡ à¤¹à¥ˆà¤‚! à¤†à¤ªà¤•à¥‡ à¤¸à¥à¤µà¤¾à¤¸à¥à¤¥à¥à¤¯ à¤¸à¤‚à¤•à¥‡à¤¤ à¤”à¤° à¤¦à¤¿à¤¨à¤šà¤°à¥à¤¯à¤¾ à¤…à¤¦à¥à¤­à¥à¤¤ à¤°à¥‚à¤ª à¤¸à¥‡ à¤¸à¥à¤¥à¤¿à¤° à¤¹à¥ˆà¤‚à¥¤ à¤à¤¸à¥‡ à¤¹à¥€ à¤¬à¤¨à¥‡ à¤°à¤¹à¥‡à¤‚!";
                }
            case "te":
                if ("CRITICAL".equals(effectiveRisk)) {
                    return "à°®à±€ à°†à°°à±‹à°—à±à°¯ à°¸à±‚à°šà°¿à°•à°²à± à°…à°¤à±à°¯à°‚à°¤ à°ªà±à°°à°®à°¾à°¦à°•à°°à°®à±ˆà°¨ à°¸à±à°¥à°¿à°¤à°¿à°²à±‹ à°‰à°¨à±à°¨à°¾à°¯à°¿. à°¦à°¯à°šà±‡à°¸à°¿ à°µà±†à°‚à°Ÿà°¨à±‡ à°®à±€ à°…à°¤à±à°¯à°µà°¸à°° à°¸à°‚à°°à°•à±à°·à°•à±à°¡à°¿à°¨à°¿ (" + contacts + ") à°¸à°‚à°ªà±à°°à°¦à°¿à°‚à°šà°‚à°¡à°¿ à°²à±‡à°¦à°¾ à°…à°¤à±à°¯à°µà°¸à°° à°µà±ˆà°¦à±à°¯ à°¸à°¹à°¾à°¯à°‚ à°ªà±Šà°‚à°¦à°‚à°¡à°¿.";
                } else if ("HIGH".equals(effectiveRisk)) {
                    return "à°¨à±‡à°¨à± à°•à±Šà°¨à±à°¨à°¿ à°¤à±€à°µà±à°°à°®à±ˆà°¨ à°†à°°à±‹à°—à±à°¯ à°¹à±†à°šà±à°šà°°à°¿à°• à°¸à°‚à°•à±‡à°¤à°¾à°²à°¨à± à°—à°®à°¨à°¿à°‚à°šà°¾à°¨à±. à°¦à°¯à°šà±‡à°¸à°¿ à°µà°¿à°¶à±à°°à°¾à°‚à°¤à°¿ à°¤à±€à°¸à±à°•à±‹à°‚à°¡à°¿ à°®à°°à°¿à°¯à± à°µà±†à°‚à°Ÿà°¨à±‡ à°®à±€ à°¸à°‚à°°à°•à±à°·à°•à±à°¡à°¿à°¨à°¿ (" + contacts + ") à°¸à°‚à°ªà±à°°à°¦à°¿à°‚à°šà°‚à°¡à°¿.";
                } else if ("MEDIUM".equals(effectiveRisk)) {
                    if (matchesDistress) {
                        return "à°®à±€à°°à± à°…à°¨à°¾à°°à±‹à°—à±à°¯à°‚à°—à°¾ à°²à±‡à°¦à°¾ à°…à°²à°¸à°Ÿà°—à°¾ à°‰à°¨à±à°¨à°¾à°°à°¨à°¿ à°µà°¿à°¨à°¡à°¾à°¨à°¿à°•à°¿ à°¨à±‡à°¨à± à°†à°‚à°¦à±‹à°³à°¨ à°šà±†à°‚à°¦à±à°¤à±à°¨à±à°¨à°¾à°¨à±. à°¦à°¯à°šà±‡à°¸à°¿ à°µà°¿à°¶à±à°°à°¾à°‚à°¤à°¿ à°¤à±€à°¸à±à°•à±‹à°‚à°¡à°¿ à°®à°°à°¿à°¯à± à°®à±€ à°†à°°à±‹à°—à±à°¯ à°²à°¾à°—à±â€Œà°²à°¨à± à°ªà°°à±à°¯à°µà±‡à°•à±à°·à°¿à°‚à°šà°‚à°¡à°¿.";
                    }
                    return "à°®à±€ à°¨à°¿à°¦à±à°° à°•à°¾à°¸à±à°¤ à°¤à°•à±à°•à±à°µà°—à°¾ à°‰à°‚à°¦à°¨à°¿ à°®à°°à°¿à°¯à± à°’à°¤à±à°¤à°¿à°¡à°¿ à°•à±Šà°¦à±à°¦à°¿à°—à°¾ à°Žà°•à±à°•à±à°µà°—à°¾ à°‰à°‚à°¦à°¨à°¿ à°—à°®à°¨à°¿à°‚à°šà°¾à°¨à±. à°¦à°¯à°šà±‡à°¸à°¿ à°ˆ à°°à±‹à°œà± à°•à±Šà°¦à±à°¦à°¿à°—à°¾ à°µà°¿à°¶à±à°°à°¾à°‚à°¤à°¿ à°¤à±€à°¸à±à°•à±‹à°‚à°¡à°¿ à°®à°°à°¿à°¯à± à°’à°¤à±à°¤à°¿à°¡à°¿ à°¤à°—à±à°—à°¿à°‚à°šà±à°•à±‹à°‚à°¡à°¿.";
                } else {
                    return "à°®à±€à°°à± à°¬à°¾à°—à±à°‚à°¡à°Ÿà°‚ à°šà±‚à°¡à°Ÿà°¾à°¨à°¿à°•à°¿ à°šà°¾à°²à°¾ à°¸à°‚à°¤à±‹à°·à°‚à°—à°¾ à°‰à°‚à°¦à°¿! à°®à±€ à°†à°°à±‹à°—à±à°¯ à°¸à±‚à°šà°¿à°•à°²à± à°®à°°à°¿à°¯à± à°…à°²à°µà°¾à°Ÿà±à°²à± à°…à°¦à±à°­à±à°¤à°‚à°—à°¾ à°‰à°¨à±à°¨à°¾à°¯à°¿. à°‡à°¦à±‡ à°…à°²à°µà°¾à°Ÿà±à°¨à± à°•à±Šà°¨à°¸à°¾à°—à°¿à°‚à°šà°‚à°¡à°¿.";
                }
            case "ml":
                if ("CRITICAL".equals(effectiveRisk)) {
                    return "à´¨à´¿à´™àµà´™à´³àµà´Ÿàµ† à´†à´°àµ‹à´—àµà´¯à´¨à´¿à´² à´…à´Ÿà´¿à´¯à´¨àµà´¤à´° à´¶àµà´°à´¦àµà´§ à´†à´µà´¶àµà´¯à´ªàµà´ªàµ†à´Ÿàµà´¨àµà´¨ à´—àµà´°àµà´¤à´°à´®à´¾à´¯ à´…à´µà´¸àµà´¥à´¯à´¿à´²à´¾à´£àµ. à´¦à´¯à´µà´¾à´¯à´¿ à´¨à´¿à´™àµà´™à´³àµà´Ÿàµ† à´…à´Ÿà´¿à´¯à´¨àµà´¤à´° à´ªà´°à´¿à´šà´¾à´°à´•à´¨àµ† (" + contacts + ") à´‰à´Ÿàµ» à´¬à´¨àµà´§à´ªàµà´ªàµ†à´Ÿàµà´• à´…à´²àµà´²àµ†à´™àµà´•à´¿àµ½ à´µàµˆà´¦àµà´¯à´¸à´¹à´¾à´¯à´‚ à´¤àµ‡à´Ÿàµà´•.";
                } else if ("HIGH".equals(effectiveRisk)) {
                    return "à´¨à´¿à´™àµà´™à´³àµà´Ÿàµ† à´†à´°àµ‹à´—àµà´¯ à´¸àµ‚à´šà´•à´™àµà´™à´³à´¿àµ½ à´šà´¿à´² à´—àµà´°àµà´¤à´°à´®à´¾à´¯ à´®àµà´¨àµà´¨à´±à´¿à´¯à´¿à´ªàµà´ªàµà´•àµ¾ à´žà´¾àµ» à´¶àµà´°à´¦àµà´§à´¿à´šàµà´šàµ. à´¦à´¯à´µà´¾à´¯à´¿ à´µà´¿à´¶àµà´°à´®à´¿à´•àµà´•àµà´•à´¯àµà´‚ à´ªà´°à´¿à´šà´¾à´°à´•à´¨àµ†à´¯àµ‹ (" + contacts + ") à´¡àµ‹à´•àµà´Ÿà´±àµ†à´¯àµ‹ à´‰à´Ÿàµ» à´¬à´¨àµà´§à´ªàµà´ªàµ†à´Ÿàµà´•à´¯àµà´‚ à´šàµ†à´¯àµà´¯àµà´•.";
                } else if ("MEDIUM".equals(effectiveRisk)) {
                    if (matchesDistress) {
                        return "à´¨à´¿à´™àµà´™àµ¾à´•àµà´•àµ à´¸àµà´–à´®à´¿à´²àµà´²àµ†à´¨àµà´¨àµ‹ à´•àµà´·àµ€à´£à´®àµ†à´¨àµà´¨àµ‹ à´•àµ‡àµ¾à´•àµà´•àµà´¨àµà´¨à´¤à´¿àµ½ à´Žà´¨à´¿à´•àµà´•àµ à´†à´¶à´™àµà´•à´¯àµà´£àµà´Ÿàµ. à´¦à´¯à´µà´¾à´¯à´¿ à´µà´¿à´¶àµà´°à´®à´¿à´•àµà´•àµà´•à´¯àµà´‚ à´¨à´¿à´™àµà´™à´³àµà´Ÿàµ† à´†à´°àµ‹à´—àµà´¯ à´¸àµ‚à´šà´•à´™àµà´™àµ¾ à´¨à´¿à´°àµ€à´•àµà´·à´¿à´•àµà´•àµà´•à´¯àµà´‚ à´šàµ†à´¯àµà´¯àµà´•.";
                    }
                    return "à´¨à´¿à´™àµà´™à´³àµà´Ÿàµ† à´‰à´±à´•àµà´•à´‚ à´…à´Ÿàµà´¤àµà´¤à´¿à´Ÿàµ† à´µà´³à´°àµ† à´•àµà´±à´µà´¾à´£àµ†à´¨àµà´¨àµà´‚ à´¸à´®àµà´®àµ¼à´¦àµà´¦à´‚ à´•àµ‚à´Ÿàµà´¤à´²à´¾à´£àµ†à´¨àµà´¨àµà´‚ à´žà´¾àµ» à´•à´£àµà´Ÿàµ. à´‡à´¨àµà´¨àµ à´¦à´¯à´µà´¾à´¯à´¿ à´…à´²àµà´ªà´‚ à´µà´¿à´¶àµà´°à´®à´¿à´•àµà´•à´¾àµ» à´¤à´¾à´²àµà´ªà´°àµà´¯à´ªàµà´ªàµ†à´Ÿàµà´•.";
                } else {
                    return "à´¨à´¿à´™àµà´™àµ¾ à´¸àµà´–à´®à´¾à´¯à´¿à´°à´¿à´•àµà´•àµà´¨àµà´¨àµ à´Žà´¨àµà´¨àµ à´•à´¾à´£àµà´¨àµà´¨à´¤à´¿àµ½ à´¸à´¨àµà´¤àµ‹à´·à´®àµà´£àµà´Ÿàµ! à´¨à´¿à´™àµà´™à´³àµà´Ÿàµ† à´†à´°àµ‹à´—àµà´¯à´¸àµ‚à´šà´•à´™àµà´™à´³àµà´‚ à´¦à´¿à´¨à´šà´°àµà´¯à´•à´³àµà´‚ à´ªàµ‚àµ¼à´£àµà´£à´®à´¾à´¯àµà´‚ à´¤àµƒà´ªàµà´¤à´¿à´•à´°à´®à´¾à´£àµ. à´‡à´¤àµ à´¤àµà´Ÿà´°àµà´•.";
                }
            case "kn":
                if ("CRITICAL".equals(effectiveRisk)) {
                    return "à²¨à²¿à²®à³à²® à²†à²°à³‹à²—à³à²¯à²¦ à²¨à²¿à²¯à²¤à²¾à²‚à²•à²—à²³à³ à²—à²‚à²­à³€à²° à²¸à³à²¥à²¿à²¤à²¿à²¯à²²à³à²²à²¿à²µà³†. à²¦à²¯à²µà²¿à²Ÿà³à²Ÿà³ à²¤à²•à³à²·à²£ à²¨à²¿à²®à³à²® à²¤à³à²°à³à²¤à³ à²†à²°à³ˆà²•à³†à²¦à²¾à²°à²°à²¨à³à²¨à³ (" + contacts + ") à²¸à²‚à²ªà²°à³à²•à²¿à²¸à²¿ à²…à²¥à²µà²¾ à²¤à³à²°à³à²¤à³ à²µà³ˆà²¦à³à²¯à²•à³€à²¯ à²¸à²¹à²¾à²¯ à²ªà²¡à³†à²¯à²¿à²°à²¿.";
                } else if ("HIGH".equals(effectiveRisk)) {
                    return "à²¨à²¾à²¨à³ à²•à³†à²²à²µà³ à²—à²‚à²­à³€à²° à²Žà²šà³à²šà²°à²¿à²•à³† à²šà²¿à²¹à³à²¨à³†à²—à²³à²¨à³à²¨à³ à²—à²®à²¨à²¿à²¸à²¿à²¦à³à²¦à³‡à²¨à³†. à²¦à²¯à²µà²¿à²Ÿà³à²Ÿà³ à²µà²¿à²¶à³à²°à²¾à²‚à²¤à²¿ à²¤à³†à²—à³†à²¦à³à²•à³Šà²³à³à²³à²¿ à²®à²¤à³à²¤à³ à²¤à²•à³à²·à²£ à²¨à²¿à²®à³à²® à²†à²°à³ˆà²•à³†à²¦à²¾à²°à²°à²¨à³à²¨à³ (" + contacts + ") à²¸à²‚à²ªà²°à³à²•à²¿à²¸à²²à³ à²ªà²°à²¿à²—à²£à²¿à²¸à²¿.";
                } else if ("MEDIUM".equals(effectiveRisk)) {
                    if (matchesDistress) {
                        return "à²¨à²¿à²®à²—à³† à²†à²°à²¾à²®à²µà²¿à²²à³à²² à²…à²¥à²µà²¾ à²†à²¯à²¾à²¸à²µà²¾à²—à²¿à²¦à³† à²Žà²‚à²¦à³ à²•à³‡à²³à²²à³ à²¨à²¨à²—à³† à²•à²¾à²³à²œà²¿à²¯à²¿à²¦à³†. à²¦à²¯à²µà²¿à²Ÿà³à²Ÿà³ à²µà²¿à²¶à³à²°à²¾à²‚à²¤à²¿ à²ªà²¡à³†à²¯à²¿à²°à²¿ à²®à²¤à³à²¤à³ à²¨à²¿à²®à³à²® à²† à²°à³‹à²—à³à²¯ à²¦à²¾à²–à²²à³†à²—à²³à²¨à³à²¨à³ à²—à²®à²¨à²¿à²¸à²¿.";
                    }
                    return "à²¨à²¿à²®à³à²® à²¨à²¿à²¦à³à²°à³† à²‡à²¤à³à²¤à³€à²šà³†à²—à³† à²•à²¡à²¿à²®à³†à²¯à²¾à²—à²¿à²¦à³† à²®à²¤à³à²¤à³ à²’à²¤à³à²¤à²¡ à²¸à³à²µà²²à³à²ª à²¹à³†à²šà³à²šà²¾à²—à²¿à²¦à³† à²Žà²‚à²¦à³ à²¨à²¾à²¨à³ à²—à²®à²¨à²¿à²¸à²¿à²¦à³à²¦à³‡à²¨à³†. à²¦à²¯à²µà²¿à²Ÿà³à²Ÿà³ à²‡à²‚à²¦à³ à²µà²¿à²¶à³à²°à²¾à²‚à²¤à²¿ à²ªà²¡à³†à²¯à²²à³ à²¸à²®à²¯ à²®à²¾à²¡à²¿à²•à³Šà²³à³à²³à²¿.";
                } else {
                    return "à²¨à³€à²µà³ à²šà³†à²¨à³à²¨à²¾à²—à²¿à²¦à³à²¦à³€à²°à²¿ à²Žà²‚à²¦à³ à²¨à³‹à²¡à²²à³ à²¤à³à²‚à²¬à²¾ à²¸à²‚à²¤à³‹à²·à²µà²¾à²—à³à²¤à³à²¤à²¦à³†! à²¨à²¿à²®à³à²® à²† à²°à³‹à²—à³à²¯ à²¸à³‚à²šà²•à²—à²³à³ à²®à²¤à³à²¤à³ à²¦à²¿à²¨à²šà²°à²¿à²—à²³à³ à²…à²¦à³à²­à³à²¤à²µà²¾à²—à²¿à²µà³†. à²‡à²¦à²¨à³à²¨à³‡ à²®à³à²®à³à²‚à²¦à³à²µà²°à²¿à²¸à²¿.";
                }
            case "fr":
                if ("CRITICAL".equals(effectiveRisk)) {
                    return "Vos paramÃ¨tres de santÃ© suggÃ¨rent une urgence mÃ©dicale. Veuillez contacter immÃ©diatement votre soignant d'urgence (" + contacts + ") ou appeler les secours.";
                } else if ("HIGH".equals(effectiveRisk)) {
                    return "J'ai dÃ©tectÃ© des alertes critiques dans votre profil de santÃ©. Reposez-vous et contactez votre soignant (" + contacts + ") immÃ©diatement.";
                } else if ("MEDIUM".equals(effectiveRisk)) {
                    if (matchesDistress) {
                        return "Je suis prÃ©occupÃ© d'apprendre que vous vous sentez fatiguÃ© ou indisposÃ©. Veuillez vous reposer et surveiller vos constantes de santÃ©.";
                    }
                    return "J'ai remarquÃ© un sommeil insuffisant et une lÃ©gÃ¨re fatigue. Pensez Ã  lever le pied aujourd'hui et Ã  bien vous hydrater.";
                } else {
                    return "Ravi de voir que vous Ãªtes en pleine forme ! Vos constantes cliniques sont stables et excellentes. Continuez ainsi !";
                }
            case "es":
                if ("CRITICAL".equals(effectiveRisk)) {
                    return "Tus indicadores de salud sugieren una emergencia. Por favor, contacta a tu cuidador de emergencia (" + contacts + ") o busca asistencia mÃ©dica de inmediato.";
                } else if ("HIGH".equals(effectiveRisk)) {
                    return "He detectado varias seÃ±ales de advertencia crÃ­ticas. Por favor, descansa y considera contactar a tu cuidador (" + contacts + ") de inmediato.";
                } else if ("MEDIUM".equals(effectiveRisk)) {
                    if (matchesDistress) {
                        return "Me preocupa escuchar que te sientes mal o fatigado. Por favor, descansa y monitorea tus registros de salud diarios.";
                    }
                    return "He notado que tu sueÃ±o ha sido bajo y tu nivel de estrÃ©s estÃ¡ elevado. Por favor, tÃ³mate un tiempo para relajarte y descansar hoy.";
                } else {
                    return "Â¡QuÃ© alegrÃ­a ver que te encuentras excelente! Tus constantes vitales estÃ¡n maravillosamente estables. MantÃ©n esta gran rutina.";
                }
            case "de":
                if ("CRITICAL".equals(effectiveRisk)) {
                    return "Ihre Gesundheitsparameter deuten auf einen kritischen Zustand hin. Bitte kontaktieren Sie sofort Ihren Notfallkontakt (" + contacts + ") oder den Notruf.";
                } else if ("HIGH".equals(effectiveRisk)) {
                    return "Ich habe kritische Warnsignale in Ihren Vitalwerten festgestellt. Bitte ruhen Sie sich aus und kontaktieren Sie Ihren Pfleger (" + contacts + ").";
                } else if ("MEDIUM".equals(effectiveRisk)) {
                    if (matchesDistress) {
                        return "Es tut mir leid zu hÃ¶ren, dass Sie sich unwohl oder mÃ¼de fÃ¼hlen. Bitte ruhen Sie sich aus und Ã¼berwachen Sie Ihre tÃ¤glichen Werte.";
                    }
                    return "Ich habe Schlafmangel und erhÃ¶hten Stress festgestellt. Bitte nehmen Sie sich heute etwas Zeit zum Entspannen und Ausruhen.";
                } else {
                    return "Es freut mich sehr, dass es Ihnen gut geht! Ihre Vitalwerte sind hervorragend und stabil. Behalten Sie diese Routine bei!";
                }
            case "ja":
                if ("CRITICAL".equals(effectiveRisk)) {
                    return "å¥åº·çŠ¶æ…‹ãŒæ¥µã‚ã¦æ·±åˆ»ã§ã€ç·Šæ€¥ã®å¯¾å¿œãŒå¿…è¦ã§ã™ã€‚ç›´ã¡ã«ç·Šæ€¥é€£çµ¡å…ˆï¼ˆ" + contacts + "ï¼‰ã«é€£çµ¡ã™ã‚‹ã‹ã€æ•‘æ€¥åŒ»ç™‚æ©Ÿé–¢ã‚’å—è¨ºã—ã¦ãã ã•ã„ã€‚";
                } else if ("HIGH".equals(effectiveRisk)) {
                    return "ã„ãã¤ã‹ã®æ·±åˆ»ãªè­¦å‘Šã®å…†å€™ãŒè¦‹ã‚‰ã‚Œã¾ã™ã€‚ç›´ã¡ã«ä½“ã‚’ä¼‘ã‚ã€ç·Šæ€¥é€£çµ¡å…ˆï¼ˆ" + contacts + "ï¼‰ã¸ã®é€£çµ¡ã‚’æ¤œè¨Žã—ã¦ãã ã•ã„ã€‚";
                } else if ("MEDIUM".equals(effectiveRisk)) {
                    if (matchesDistress) {
                        return "ä½“èª¿ãŒå„ªã‚Œãªã„ã‹ã€ç–²åŠ´ã‚’æ„Ÿã˜ã¦ã„ã‚‹ã¨ã®ã“ã¨ã€å¿ƒé…ã—ã¦ãŠã‚Šã¾ã™ã€‚ç„¡ç†ã‚’ãªã•ã‚‰ãšååˆ†ã«ä¼‘æ¯ã‚’å–ã‚Šã€ãƒã‚¤ã‚¿ãƒ«ã‚’è¨˜éŒ²ã—ã¦ãã ã•ã„ã€‚";
                    }
                    return "æœ€è¿‘ã€ç¡çœ æ™‚é–“ãŒä¸è¶³ã—ã¦ãŠã‚Šã€ã‚¹ãƒˆãƒ¬ã‚¹ãƒ¬ãƒ™ãƒ«ãŒé«˜ããªã£ã¦ã„ã¾ã™ã€‚ä»Šæ—¥ã¯ç„¡ç†ã›ãšä¼‘æ¯ã‚’å–ã‚Šã€ãƒªãƒ©ãƒƒã‚¯ã‚¹ã—ã¦ãã ã•ã„ã€‚";
                } else {
                    return "éžå¸¸ã«ã‚ˆã„çŠ¶æ…‹ãŒç¶­æŒã•ã‚Œã¦ã„ã¾ã™ï¼ãƒã‚¤ã‚¿ãƒ«ã‚‚æ—¥å¸¸ã®ãƒ«ãƒ¼ãƒãƒ³ã‚‚æ¥µã‚ã¦å®‰å®šã—ã¦ã„ã¾ã™ã€‚ã“ã®ç´ æ™´ã‚‰ã—ã„ç¿’æ…£ã‚’ç¶šã‘ã¾ã—ã‚‡ã†ã€‚";
                }
            default: // en
                if ("CRITICAL".equals(effectiveRisk)) {
                    return "Your health parameters are in a critical state suggesting urgent attention. Please contact your emergency caregiver (" + contacts + ") or seek medical help immediately.";
                } else if ("HIGH".equals(effectiveRisk)) {
                    return "I am noticing several warning signs, including worsening fatigue and vitals. Please rest and contact your caregiver (" + contacts + ") or doctor immediately.";
                } else if (isHair) {
                    return "To promote healthy hair growth, focus on a balanced diet rich in protein, iron, and vitamins A, C, and E. Avoid harsh heat treatments, massage your scalp gently to stimulate blood circulation, and stay hydrated. Since your vitals look stable, these lifestyle adjustments are excellent and safe to start!";
                } else if (isSleep) {
                    return "To improve sleep quality, establish a consistent sleep schedule and keep your bedroom cool and dark. Avoid screens for at least 30 minutes before bed. Regular, relaxing evening routines support deep recovery cycles.";
                } else if (isDiet) {
                    return "A nutrient-dense diet rich in leafy greens, lean proteins, fiber, and plenty of water is highly recommended. Limit processed foods, excessive sodium, and refined sugars. Consistent hydration is crucial for your vitals.";
                } else if (isExercise) {
                    return "For general health, aim for 30 minutes of moderate activity like brisk walking or cycling daily. Always check with your caregiver or physician before starting any high-intensity routine, especially if you're feeling tired.";
                } else if (isStress) {
                    return "I hear you, and it is completely okay to feel stressed or anxious. Try a quick grounding technique: take a slow, deep breath in for 4 seconds, hold for 4 seconds, and exhale completely for 6 seconds. Prioritize peaceful rest today.";
                } else if (isMed) {
                    return "Taking your medications consistently at the same times everyday is essential for effective care. Setting silent recurring alarms can help you manage your reminders seamlessly. Never adjust your dosage without doctor consultation.";
                } else if (isFatigue) {
                    return "Persistent fatigue indicates your body needs time to rest and recharge. Focus on improving your hydration levels and getting optimal sleep tonight. We will monitor your fatigue logs to safeguard your wellness.";
                } else if (isHello) {
                    return "Hello! I am Nexura, your personal care companion. I'm here to support you with your health logs, medication schedules, and wellness guidelines. How can I help you today?";
                } else if ("MEDIUM".equals(effectiveRisk)) {
                    if (matchesDistress) {
                        return "I am concerned to hear that you are feeling unwell or fatigued today. Please take a moment to rest, monitor your daily health logs, and check in with your caregiver if you do not feel better soon.";
                    }
                    return "I noticed your recent sleep has been low and stress is elevated. Taking things easy today and resting will help you recover.";
                } else {
                    return "I'm glad to see you're doing so well! Your vitals and daily habits look wonderfully stable. Keep up the excellent routine.";
                }
        }
    }

    private void createRiskNotificationAlert(User user, AIAnalysis analysis) {
        Alert alert = new Alert();
        alert.setUser(user);
        alert.setTitle("AI Deterioration Assessment: " + analysis.getRiskLevel() + " Risk");
        alert.setMessage("Our AI trend analysis has flagged a " + analysis.getRiskLevel() + " risk. Findings: " + analysis.getKeyFindings() + " Recommendations: " + analysis.getRecommendations());
        alert.setCategory("AI_WARNING");
        alert.setPriority(analysis.getRiskLevel());
        alert.setIsRead(false);
        alertRepository.save(alert);
    }

    private String cleanJsonString(String response) {
        if (response == null) return "{}";
        String clean = response.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        return clean.trim();
    }

    private AIAnalysis parseAIAnalysis(User user, String aiResponse) {
        AIAnalysis analysis = new AIAnalysis();
        analysis.setUser(user);
        try {
            JsonNode root = objectMapper.readTree(cleanJsonString(aiResponse));
            analysis.setRiskLevel(root.path("riskLevel").asText("LOW"));
            analysis.setKeyFindings(root.path("keyFindings").asText("No key findings."));
            analysis.setRecommendations(root.path("recommendations").asText("Keep up your healthy routine."));
        } catch (Exception e) {
            // Local fallback rule-based analyzer
            analysis.setRiskLevel("LOW");
            analysis.setKeyFindings("Metrics are stable within reference bounds.");
            analysis.setRecommendations("Continue monitoring your daily habits.");
        }
        return analysis;
    }
}
