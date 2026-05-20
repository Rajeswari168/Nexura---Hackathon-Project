package com.nexura.app.repository;

import com.nexura.app.entity.EmergencyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmergencyEventRepository extends JpaRepository<EmergencyEvent, Long> {
    List<EmergencyEvent> findByUserIdOrderByEscalatedAtDesc(Long userId);
}
