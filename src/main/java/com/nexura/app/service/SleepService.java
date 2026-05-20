package com.nexura.app.service;

import com.nexura.app.entity.Alert;
import com.nexura.app.entity.SleepLog;
import com.nexura.app.entity.User;
import com.nexura.app.repository.AlertRepository;
import com.nexura.app.repository.SleepLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SleepService {

    @Autowired
    private SleepLogRepository sleepLogRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private UserService userService;

    @Transactional
    public SleepLog logSleep(SleepLog log) {
        User user = userService.getCurrentUser();
        log.setUser(user);

        // Calculate duration dynamically if not set
        if (log.getDurationHours() == null || log.getDurationHours() <= 0) {
            if (log.getSleepStartTime() != null && log.getWakeTime() != null) {
                Duration duration = Duration.between(log.getSleepStartTime(), log.getWakeTime());
                double hours = duration.toMinutes() / 60.0;
                log.setDurationHours(hours);
            } else {
                log.setDurationHours(8.0); // Default placeholder
            }
        }

        SleepLog savedLog = sleepLogRepository.save(log);

        // Run sleep trend deterioration logic
        checkSleepDeterioration(user);

        return savedLog;
    }

    public List<SleepLog> getHistory() {
        User user = userService.getCurrentUser();
        return sleepLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    private void checkSleepDeterioration(User user) {
        List<SleepLog> sleepHistory = sleepLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        
        if (sleepHistory.size() >= 3) {
            SleepLog s1 = sleepHistory.get(0);
            SleepLog s2 = sleepHistory.get(1);
            SleepLog s3 = sleepHistory.get(2);

            // Check if user slept less than 5 hours for 3 consecutive entries
            if (s1.getDurationHours() < 5.0 && s2.getDurationHours() < 5.0 && s3.getDurationHours() < 5.0) {
                createSleepAlert(user, "Sleep Deterioration Flagged",
                        "We noticed you have slept less than 5 hours for 3 consecutive days. This abnormal sleep reduction can affect your health. We will shift non-critical reminders to support your rest.",
                        "HIGH");
            }
            // Check if sleep quality is poor (e.g. <= 2) for 3 consecutive entries
            else if (s1.getSleepQuality() != null && s1.getSleepQuality() <= 2 &&
                     s2.getSleepQuality() != null && s2.getSleepQuality() <= 2 &&
                     s3.getSleepQuality() != null && s3.getSleepQuality() <= 2) {
                createSleepAlert(user, "Abnormal Sleep Consistency",
                        "Your logged sleep quality has been poor for 3 consecutive logs. High stress or symptoms may be disturbing your rest. Consider using our AI chatbot for relaxation tips.",
                        "MEDIUM");
            }
        }
    }

    private void createSleepAlert(User user, String title, String message, String priority) {
        // Prevent creating duplicate sleep alerts if one was just created recently
        List<Alert> existingAlerts = alertRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        boolean hasRecentAlert = existingAlerts.stream()
                .limit(3)
                .anyMatch(a -> a.getTitle().equals(title) && a.getCreatedAt() != null &&
                        a.getCreatedAt().isAfter(LocalDateTime.now().minusDays(1)));

        if (!hasRecentAlert) {
            Alert alert = new Alert();
            alert.setUser(user);
            alert.setTitle(title);
            alert.setMessage(message);
            alert.setCategory("SLEEP_DETERIORATION");
            alert.setPriority(priority);
            alert.setIsRead(false);
            alertRepository.save(alert);
        }
    }
}
