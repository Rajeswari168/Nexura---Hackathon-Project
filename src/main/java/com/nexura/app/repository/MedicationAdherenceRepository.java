package com.nexura.app.repository;

import com.nexura.app.entity.MedicationAdherence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MedicationAdherenceRepository extends JpaRepository<MedicationAdherence, Long> {
    List<MedicationAdherence> findByMedicationId(Long medicationId);
    Optional<MedicationAdherence> findByMedicationIdAndScheduledDate(Long medicationId, LocalDate scheduledDate);
    List<MedicationAdherence> findByMedicationUserId(Long userId);
    List<MedicationAdherence> findByMedicationUserIdAndScheduledDateBetween(Long userId, LocalDate start, LocalDate end);
}
