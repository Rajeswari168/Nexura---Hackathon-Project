package com.nexura.app.service;

import com.nexura.app.entity.Medication;
import com.nexura.app.entity.Reminder;
import com.nexura.app.entity.SleepLog;
import com.nexura.app.entity.User;
import com.nexura.app.repository.MedicationRepository;
import com.nexura.app.repository.ReminderRepository;
import com.nexura.app.repository.SleepLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReminderService {

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private MedicationRepository medicationRepository;

    @Autowired
    private SleepLogRepository sleepLogRepository;

    @Autowired
    private UserService userService;

    @Transactional
    public List<Reminder> getTodayReminders() {
        User user = userService.getCurrentUser();
        LocalDate today = LocalDate.now();

        // 1. Generate standard baseline reminders for today if not already initialized
        initializeRemindersForToday(user, today);

        // 2. Fetch and return today's reminders
        return reminderRepository.findByUserIdAndStatusOrderByScheduledTimeAsc(user.getId(), "PENDING");
    }

    @Transactional
    public List<Reminder> adaptReminders() {
        User user = userService.getCurrentUser();
        List<Reminder> todayReminders = getTodayReminders();

        // Check if poor sleep occurred last night (< 5 hours)
        boolean hasPoorSleep = false;
        List<SleepLog> sleepHistory = sleepLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        if (!sleepHistory.isEmpty()) {
            SleepLog lastSleep = sleepHistory.get(0);
            if (lastSleep.getDurationHours() < 5.0) {
                hasPoorSleep = true;
            }
        }

        List<Reminder> adaptedList = new ArrayList<>();

        for (Reminder reminder : todayReminders) {
            Medication med = reminder.getMedication();
            boolean changed = false;

            // Rule A: Sleep-based Delaying
            // If poor sleep was logged, delay non-critical morning reminders by 90 minutes
            if (hasPoorSleep && reminder.getScheduledTime().getHour() < 12) {
                reminder.setScheduledTime(reminder.getScheduledTime().plusMinutes(90));
                reminder.setDelayMinutes(90);
                reminder.setNotesOverride("Delayed 90m due to poor sleep analysis.");
                changed = true;
            }

            // Rule B: Lateness Adaptation
            // Analyze average delay across past logged intakes of this medication
            if (!changed) {
                List<Reminder> pastIntakes = reminderRepository.findByMedicationIdOrderByScheduledTimeDesc(med.getId());
                double avgDelay = pastIntakes.stream()
                        .filter(r -> "TAKEN".equals(r.getStatus()) && r.getDelayMinutes() > 0)
                        .limit(5)
                        .mapToInt(Reminder::getDelayMinutes)
                        .average()
                        .orElse(0.0);

                if (avgDelay > 15.0) {
                    int delayShift = (int) avgDelay;
                    reminder.setScheduledTime(reminder.getScheduledTime().plusMinutes(delayShift));
                    reminder.setDelayMinutes(delayShift);
                    reminder.setNotesOverride("Shifted +" + delayShift + "m based on lateness patterns.");
                    changed = true;
                }
            }

            if (changed) {
                reminder.setStatus("PENDING");
                adaptedList.add(reminderRepository.save(reminder));
            }
        }

        return adaptedList;
    }

    @Transactional
    public Reminder snoozeReminder(Long reminderId) {
        Reminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new RuntimeException("Reminder event not found"));

        // Rule C: Snooze retry escalation
        // Snooze intervals escalate closer as they are ignored (e.g. starts at 15m, then 10m, then 5m)
        int currentInterval = reminder.getAdaptiveIntervalMinutes();
        int nextInterval = 10; // Default snooze
        if (currentInterval == 0) {
            nextInterval = 15;
        } else if (currentInterval == 15) {
            nextInterval = 10;
        } else if (currentInterval == 10) {
            nextInterval = 5;
        } else if (currentInterval == 5) {
            nextInterval = 3;
        }

        reminder.setStatus("SNOOZED");
        reminder.setAdaptiveIntervalMinutes(nextInterval);
        // Shift scheduled time for immediate retry alert
        reminder.setScheduledTime(LocalDateTime.now().plusMinutes(nextInterval));
        
        return reminderRepository.save(reminder);
    }

    private void initializeRemindersForToday(User user, LocalDate today) {
        List<Medication> activeMeds = medicationRepository.findByUserIdAndActiveTrue(user.getId());
        List<Reminder> existingReminders = reminderRepository.findByUserIdAndStatusOrderByScheduledTimeAsc(user.getId(), "PENDING");

        for (Medication med : activeMeds) {
            LocalTime schedTime = LocalTime.parse(med.getScheduledTime());
            LocalDateTime schedDateTime = LocalDateTime.of(today, schedTime);

            // Check if already created
            boolean exists = existingReminders.stream()
                    .anyMatch(r -> r.getMedication().getId().equals(med.getId()) &&
                            r.getScheduledTime().toLocalDate().equals(today));

            if (!exists) {
                Reminder reminder = new Reminder();
                reminder.setUser(user);
                reminder.setMedication(med);
                reminder.setScheduledTime(schedDateTime);
                reminder.setStatus("PENDING");
                reminder.setDelayMinutes(0);
                reminder.setAdaptiveIntervalMinutes(0);
                reminderRepository.save(reminder);
            }
        }
    }
}
