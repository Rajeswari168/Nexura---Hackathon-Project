package com.nexura.app.repository;

import com.nexura.app.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Alert> findByUserIdAndIsReadOrderByCreatedAtDesc(Long userId, boolean isRead);
}
