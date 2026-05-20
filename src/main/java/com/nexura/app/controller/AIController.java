package com.nexura.app.controller;

import com.nexura.app.entity.AIAnalysis;
import com.nexura.app.entity.ReportAnalysis;
import com.nexura.app.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AIController {

    @Autowired
    private AIService aiService;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeOverallDeterioration() {
        try {
            AIAnalysis analysis = aiService.analyzeOverallDeterioration();
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to perform clinical trend analysis: " + e.getMessage());
        }
    }

    @GetMapping("/risk-status")
    public ResponseEntity<?> getRiskStatus() {
        try {
            AIAnalysis status = aiService.getLatestRiskStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to fetch risk status: " + e.getMessage());
        }
    }

    @PostMapping("/analyze-report/{reportId}")
    public ResponseEntity<?> analyzeReport(@PathVariable Long reportId) {
        try {
            ReportAnalysis analysis = aiService.analyzeMedicalReport(reportId);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to perform document analysis: " + e.getMessage());
        }
    }
}
