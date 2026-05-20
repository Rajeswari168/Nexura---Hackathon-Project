package com.nexura.app.service;

import com.nexura.app.entity.Alert;
import com.nexura.app.entity.EmergencyEvent;
import com.nexura.app.entity.User;
import com.nexura.app.repository.AlertRepository;
import com.nexura.app.repository.EmergencyEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmergencyService {

    @Autowired
    private EmergencyEventRepository emergencyEventRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private UserService userService;

    @Transactional
    public EmergencyEvent escalate(String reason) {
        User user = userService.getCurrentUser();

        EmergencyEvent event = new EmergencyEvent();
        event.setUser(user);
        event.setTriggerReason(reason);
        event.setStatus("ESCALATED");

        EmergencyEvent saved = emergencyEventRepository.save(event);

        // 1. Create a critical alert
        Alert alert = new Alert();
        alert.setUser(user);
        alert.setTitle("CRITICAL EMERGENCY ESCALATION");
        alert.setMessage("Escalation triggered. Reason: " + reason);
        alert.setCategory("CRITICAL_EMERGENCY");
        alert.setPriority("CRITICAL");
        alert.setIsRead(false);
        alertRepository.save(alert);

        // 2. Simulate SMS transmission (Print to console as mock audit logs)
        System.out.println("-----------------------------------------------------------------");
        System.out.println("[SMS ENGINE WARNING] Dispatched to Caregiver: " + user.getEmergencyCaregiverName());
        System.out.println("Recipient Phone: " + user.getCaregiverPhoneNumber());
        System.out.println("Alert Body: Emergency Alert for " + user.getFullName() + ". Reason: " + reason);
        System.out.println("-----------------------------------------------------------------");

        // 3. Simulate Email transmission
        System.out.println("-----------------------------------------------------------------");
        System.out.println("[EMAIL NOTIFICATION] Dispatched from Nexura Cloud Services");
        System.out.println("Recipient Name: " + user.getEmergencyCaregiverName());
        System.out.println("Alert Topic: Critical Patient Notification - " + user.getFullName());
        System.out.println("Body: This is an automated escalation warning. Patient " + user.getFullName() +
                " is experiencing physiological trend degradation or has missed several scheduled medication doses. Please contact them immediately.");
        System.out.println("-----------------------------------------------------------------");

        return saved;
    }

    public List<EmergencyEvent> getHistory() {
        User user = userService.getCurrentUser();
        return emergencyEventRepository.findByUserIdOrderByEscalatedAtDesc(user.getId());
    }
}
