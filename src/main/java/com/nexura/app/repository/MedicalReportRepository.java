package com.nexura.app.repository;

import com.nexura.app.entity.MedicalReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalReportRepository extends JpaRepository<MedicalReport, Long> {
    List<MedicalReport> findByUserIdOrderByUploadedAtDesc(Long userId);
}
