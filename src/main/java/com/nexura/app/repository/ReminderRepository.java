package com.nexura.app.repository;

import com.nexura.app.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    List<Reminder> findByUserIdOrderByScheduledTimeDesc(Long userId);
    List<Reminder> findByUserIdAndStatusOrderByScheduledTimeAsc(Long userId, String status);
    List<Reminder> findByMedicationIdOrderByScheduledTimeDesc(Long medicationId);
}
