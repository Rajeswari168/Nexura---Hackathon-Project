package com.nexura.app.controller;

import com.nexura.app.entity.SleepLog;
import com.nexura.app.service.SleepService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sleep")
@CrossOrigin(origins = "*")
public class SleepController {

    @Autowired
    private SleepService sleepService;

    @PostMapping("/log")
    public ResponseEntity<?> logSleep(@RequestBody SleepLog sleepLog) {
        try {
            SleepLog saved = sleepService.logSleep(sleepLog);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to log sleep patterns: " + e.getMessage());
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<SleepLog>> getHistory() {
        List<SleepLog> history = sleepService.getHistory();
        return ResponseEntity.ok(history);
    }
}
