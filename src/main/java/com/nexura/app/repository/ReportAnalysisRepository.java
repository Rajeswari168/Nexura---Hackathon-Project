package com.nexura.app.repository;

import com.nexura.app.entity.ReportAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportAnalysisRepository extends JpaRepository<ReportAnalysis, Long> {
    Optional<ReportAnalysis> findByReportId(Long reportId);
}
