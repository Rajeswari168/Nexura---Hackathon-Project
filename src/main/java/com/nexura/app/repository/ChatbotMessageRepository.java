package com.nexura.app.repository;

import com.nexura.app.entity.ChatbotMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatbotMessageRepository extends JpaRepository<ChatbotMessage, Long> {
    List<ChatbotMessage> findByUserIdOrderByTimestampAsc(Long userId);
}
