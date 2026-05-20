package com.nexura.app.controller;

import com.nexura.app.entity.ChatbotMessage;
import com.nexura.app.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private AIService aiService;

    @PostMapping("/message")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, Object> payload) {
        String msg = (String) payload.get("message");
        String language = (String) payload.get("language");
        Boolean voiceModeObj = (Boolean) payload.get("voiceMode");
        boolean voiceMode = voiceModeObj != null ? voiceModeObj : false;

        if (msg == null || msg.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message content cannot be empty."));
        }
        if (language == null || language.trim().isEmpty()) {
            language = "en";
        }

        try {
            ChatbotMessage response = aiService.generateChatbotResponse(msg, language, voiceMode);
            
            Map<String, Object> responseBody = Map.of(
                "reply", response.getAiResponse(),
                "riskLevel", response.getRiskLevel() != null ? response.getRiskLevel() : "LOW",
                "emotionMode", response.getEmotionMode() != null ? response.getEmotionMode() : "NORMAL"
            );
            return ResponseEntity.ok(responseBody);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to process message: " + e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<ChatbotMessage>> getHistory() {
        return ResponseEntity.ok(aiService.getChatHistory());
    }

    @DeleteMapping("/history")
    public ResponseEntity<?> clearHistory() {
        try {
            aiService.clearChatHistory();
            return ResponseEntity.ok(Map.of("message", "Chat history successfully cleared."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to clear chat history: " + e.getMessage()));
        }
    }
}
