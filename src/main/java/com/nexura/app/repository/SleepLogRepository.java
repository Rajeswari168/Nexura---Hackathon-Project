package com.nexura.app.repository;

import com.nexura.app.entity.SleepLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SleepLogRepository extends JpaRepository<SleepLog, Long> {
    List<SleepLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<SleepLog> findByUserIdOrderByCreatedAtAsc(Long userId);
}
