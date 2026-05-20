package com.nexura.app.controller;

import com.nexura.app.entity.Reminder;
import com.nexura.app.service.ReminderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reminders")
@CrossOrigin(origins = "*")
public class ReminderController {

    @Autowired
    private ReminderService reminderService;

    @GetMapping
    public ResponseEntity<List<Reminder>> getTodayReminders() {
        return ResponseEntity.ok(reminderService.getTodayReminders());
    }

    @PostMapping("/adapt")
    public ResponseEntity<?> adaptReminders() {
        try {
            List<Reminder> adapted = reminderService.adaptReminders();
            return ResponseEntity.ok(adapted);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to execute reminder adaptation: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/snooze")
    public ResponseEntity<?> snoozeReminder(@PathVariable Long id) {
        try {
            Reminder snoozed = reminderService.snoozeReminder(id);
            return ResponseEntity.ok(snoozed);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to snooze reminder: " + e.getMessage());
        }
    }
}
