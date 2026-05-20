package com.nexura.app.controller;

import com.nexura.app.entity.HealthLog;
import com.nexura.app.service.HealthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthController {

    @Autowired
    private HealthService healthService;

    @PostMapping("/log")
    public ResponseEntity<?> logHealth(@RequestBody HealthLog healthLog) {
        try {
            HealthLog saved = healthService.logHealth(healthLog);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to log health vitals: " + e.getMessage());
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<HealthLog>> getHistory() {
        List<HealthLog> history = healthService.getHistory();
        return ResponseEntity.ok(history);
    }
}
