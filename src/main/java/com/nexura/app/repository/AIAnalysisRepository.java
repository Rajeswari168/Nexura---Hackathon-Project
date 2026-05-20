package com.nexura.app.repository;

import com.nexura.app.entity.AIAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIAnalysisRepository extends JpaRepository<AIAnalysis, Long> {
    List<AIAnalysis> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<AIAnalysis> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
