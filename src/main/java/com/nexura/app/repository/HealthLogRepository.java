package com.nexura.app.repository;

import com.nexura.app.entity.HealthLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HealthLogRepository extends JpaRepository<HealthLog, Long> {
    List<HealthLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<HealthLog> findByUserIdOrderByCreatedAtAsc(Long userId);
}
