package com.nexura.app.service;

import com.nexura.app.entity.Medication;
import com.nexura.app.entity.MedicationAdherence;
import com.nexura.app.entity.Reminder;
import com.nexura.app.entity.User;
import com.nexura.app.repository.MedicationAdherenceRepository;
import com.nexura.app.repository.MedicationRepository;
import com.nexura.app.repository.ReminderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class MedicationService {

    @Autowired
    private MedicationRepository medicationRepository;

    @Autowired
    private MedicationAdherenceRepository adherenceRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private UserService userService;

    public List<Medication> getAll() {
        User user = userService.getCurrentUser();
        return medicationRepository.findByUserIdAndActiveTrue(user.getId());
    }

    public Medication add(Medication med) {
        User user = userService.getCurrentUser();
        med.setUser(user);
        med.setActive(true);
        return medicationRepository.save(med);
    }

    public Medication update(Long id, Medication medDetails) {
        User user = userService.getCurrentUser();
        Medication med = medicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medication not found"));

        if (!med.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized edit attempt");
        }

        med.setName(medDetails.getName());
        med.setDosage(medDetails.getDosage());
        med.setScheduledTime(medDetails.getScheduledTime());
        med.setFrequency(medDetails.getFrequency());
        med.setNotes(medDetails.getNotes());

        return medicationRepository.save(med);
    }

    public void delete(Long id) {
        User user = userService.getCurrentUser();
        Medication med = medicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medication not found"));

        if (!med.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized delete attempt");
        }

        // Soft delete: turn active status off
        med.setActive(false);
        medRepositorySave(med);
    }

    private void medRepositorySave(Medication med) {
        medificationSave(med);
    }

    private void medificationSave(Medication med) {
        medicationRepository.save(med);
    }

    @Transactional
    public MedicationAdherence markAsTaken(Long id) {
        User user = userService.getCurrentUser();
        Medication med = medicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Medication not found"));

        if (!med.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized adherence logging attempt");
        }

        LocalDate today = LocalDate.now();

        // Check if an adherence log already exists for today
        Optional<MedicationAdherence> existing = adherenceRepository.findByMedicationIdAndScheduledDate(id, today);
        MedicationAdherence adherence;
        if (existing.isPresent()) {
            adherence = existing.get();
            adherence.setStatus("TAKEN");
            adherence.setTakenAt(LocalDateTime.now());
        } else {
            adherence = new MedicationAdherence();
            adherence.setMedication(med);
            adherence.setScheduledDate(today);
            adherence.setStatus("TAKEN");
            adherence.setTakenAt(LocalDateTime.now());
        }

        adherence = adherenceRepository.save(adherence);

        // Record lateness tracking in Reminder engine
        recordLatenessDelay(user, med, adherence.getTakenAt());

        return adherence;
    }

    public double getWeeklyComplianceRate() {
        User user = userService.getCurrentUser();
        LocalDate start = LocalDate.now().minusDays(6);
        LocalDate end = LocalDate.now();
        List<MedicationAdherence> adherenceList = adherenceRepository
                .findByMedicationUserIdAndScheduledDateBetween(user.getId(), start, end);

        if (adherenceList.isEmpty()) {
            return 100.0; // Assume perfect baseline compliance if no logged dates
        }

        long taken = adherenceList.stream().filter(a -> "TAKEN".equals(a.getStatus())).count();
        return (double) taken / adherenceList.size() * 100.0;
    }

    private void recordLatenessDelay(User user, Medication med, LocalDateTime takenAt) {
        // Scheduled time parses as "HH:MM"
        try {
            LocalTime schedTime = LocalTime.parse(med.getScheduledTime());
            LocalDateTime schedDateTime = LocalDateTime.of(LocalDate.now(), schedTime);
            
            // Calculate delay in minutes
            long delay = Duration.between(schedDateTime, takenAt).toMinutes();
            
            // Create or update a Reminder event for audit tracking
            Reminder reminder = new Reminder();
            reminder.setUser(user);
            reminder.setMedication(med);
            reminder.setScheduledTime(schedDateTime);
            reminder.setActualTime(takenAt);
            reminder.setStatus("TAKEN");
            
            if (delay > 0) {
                reminder.setDelayMinutes((int) delay);
            } else {
                reminder.setDelayMinutes(0); // Taken on-time or early
            }
            
            reminderRepository.save(reminder);
        } catch (Exception e) {
            // Log parse errors gracefully
        }
    }
}
