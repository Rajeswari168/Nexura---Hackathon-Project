package com.nexura.app.controller;

import com.nexura.app.dto.AuthResponse;
import com.nexura.app.dto.LoginRequest;
import com.nexura.app.dto.RegisterRequest;
import com.nexura.app.entity.User;
import com.nexura.app.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            AuthResponse response = userService.register(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthResponse response = userService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid credentials: " + e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe() {
        try {
            User user = userService.getCurrentUser();
            // Clear password hash before transmitting
            user.setPassword(null);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PostMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody java.util.Map<String, String> settings) {
        try {
            User user = userService.getCurrentUser();
            if (settings.containsKey("aiProvider")) {
                user.setAiProvider(settings.get("aiProvider"));
            }
            if (settings.containsKey("groqKey")) {
                user.setGroqKey(settings.get("groqKey"));
            }
            if (settings.containsKey("geminiKey")) {
                user.setGeminiKey(settings.get("geminiKey"));
            }
            if (settings.containsKey("openaiKey")) {
                user.setOpenaiKey(settings.get("openaiKey"));
            }
            userService.save(user);
            return ResponseEntity.ok(java.util.Map.of("message", "AI Companion settings updated successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
