package com.nexura.app.service;

import com.nexura.app.entity.Alert;
import com.nexura.app.entity.HealthLog;
import com.nexura.app.entity.User;
import com.nexura.app.repository.AlertRepository;
import com.nexura.app.repository.HealthLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class HealthService {

    @Autowired
    private HealthLogRepository healthLogRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private UserService userService;

    @Transactional
    public HealthLog logHealth(HealthLog log) {
        User user = userService.getCurrentUser();
        log.setUser(user);
        
        HealthLog savedLog = healthLogRepository.save(log);

        // Run automated vitals clinical bounds safeguarding
        checkVitalsSafeguards(user, savedLog);

        return savedLog;
    }

    public List<HealthLog> getHistory() {
        User user = userService.getCurrentUser();
        return healthLogRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    private void checkVitalsSafeguards(User user, HealthLog log) {
        // 1. Oxygen Vitals Check
        if (log.getOxygenLevel() != null && log.getOxygenLevel() < 90) {
            createVitalAlert(user, "Critical Oxygen Level",
                    "Your blood oxygen level is critically low at " + log.getOxygenLevel() + "%. Please contact your emergency caregiver immediately or seek medical care.",
                    "ABNORMAL_VITALS", "CRITICAL");
        } else if (log.getOxygenLevel() != null && log.getOxygenLevel() < 95) {
            createVitalAlert(user, "Mild Hypoxia Warning",
                    "Your blood oxygen level is low at " + log.getOxygenLevel() + "%. Please monitor your breathing closely.",
                    "ABNORMAL_VITALS", "MEDIUM");
        }

        // 2. Heart Rate Vitals Check
        if (log.getHeartRate() != null) {
            if (log.getHeartRate() > 120) {
                createVitalAlert(user, "Tachycardia Alert",
                        "Your heart rate is dangerously high at " + log.getHeartRate() + " BPM. Rest quietly, and if it stays elevated, notify your caregiver.",
                        "ABNORMAL_VITALS", "HIGH");
            } else if (log.getHeartRate() < 50) {
                createVitalAlert(user, "Bradycardia Alert",
                        "Your heart rate is low at " + log.getHeartRate() + " BPM. If you feel dizzy or fatigued, notify your caregiver.",
                        "ABNORMAL_VITALS", "HIGH");
            }
        }

        // 3. Temperature Check
        if (log.getBodyTemperature() != null) {
            if (log.getBodyTemperature() > 39.0) {
                createVitalAlert(user, "High Fever Warning",
                        "Your body temperature is high at " + log.getBodyTemperature() + "°C. Keep hydrated and check in with medical resources.",
                        "ABNORMAL_VITALS", "HIGH");
            } else if (log.getBodyTemperature() < 35.0) {
                createVitalAlert(user, "Hypothermia Warning",
                        "Your body temperature is abnormally low at " + log.getBodyTemperature() + "°C. Stay warm and seek assistance if symptoms persist.",
                        "ABNORMAL_VITALS", "HIGH");
            }
        }

        // 4. Stress Vitals Check
        if (log.getStressLevel() != null && log.getStressLevel() >= 8) {
            createVitalAlert(user, "Extreme Stress Detected",
                    "Your logged stress level is very high (" + log.getStressLevel() + "/10). Take a slow breathing break or consider a short rest.",
                    "AI_WARNING", "MEDIUM");
        }
    }

    private void createVitalAlert(User user, String title, String message, String category, String priority) {
        Alert alert = new Alert();
        alert.setUser(user);
        alert.setTitle(title);
        alert.setMessage(message);
        alert.setCategory(category);
        alert.setPriority(priority);
        alert.setIsRead(false);
        alertRepository.save(alert);
    }
}
