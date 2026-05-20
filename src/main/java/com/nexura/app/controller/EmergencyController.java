package com.nexura.app.controller;

import com.nexura.app.entity.EmergencyEvent;
import com.nexura.app.service.EmergencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emergency")
@CrossOrigin(origins = "*")
public class EmergencyController {

    @Autowired
    private EmergencyService emergencyService;

    @PostMapping("/escalate")
    public ResponseEntity<?> escalate(@RequestBody Map<String, String> payload) {
        String reason = payload.get("reason");
        if (reason == null || reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Escalation trigger reason cannot be empty.");
        }
        try {
            EmergencyEvent event = emergencyService.escalate(reason);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "eventId", event.getId(),
                    "smsStatus", "DISPATCHED",
                    "emailStatus", "DISPATCHED",
                    "message", "Simulated SMS & Email alerts dispatched to Caregiver successfully!"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to escalate emergency event: " + e.getMessage());
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<EmergencyEvent>> getHistory() {
        return ResponseEntity.ok(emergencyService.getHistory());
    }
}
