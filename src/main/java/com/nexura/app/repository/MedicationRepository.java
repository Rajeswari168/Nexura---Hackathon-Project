package com.nexura.app.repository;

import com.nexura.app.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicationRepository extends JpaRepository<Medication, Long> {
    List<Medication> findByUserId(Long userId);
    List<Medication> findByUserIdAndActiveTrue(Long userId);
}
