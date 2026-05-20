package com.nexura.app.controller;

import com.nexura.app.entity.Alert;
import com.nexura.app.entity.User;
import com.nexura.app.repository.AlertRepository;
import com.nexura.app.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "*")
public class AlertController {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<Alert>> getAlerts() {
        User user = userService.getCurrentUser();
        List<Alert> alerts = alertRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return ResponseEntity.ok(alerts);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        try {
            User user = userService.getCurrentUser();
            Alert alert = alertRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Alert not found"));

            if (!alert.getUser().getId().equals(user.getId())) {
                throw new RuntimeException("Unauthorized alert access");
            }

            alert.setIsRead(true);
            alertRepository.save(alert);
            return ResponseEntity.ok(Map.of("message", "Alert marked as read successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to modify alert status: " + e.getMessage());
        }
    }
}
