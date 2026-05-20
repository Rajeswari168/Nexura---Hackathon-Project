package com.nexura.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "chatbot_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "ai_response", nullable = false, columnDefinition = "TEXT")
    private String aiResponse;

    @Column(nullable = false, length = 50)
    private String language;

    @Column(name = "voice_used", nullable = false)
    private Boolean voiceUsed;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Transient
    private String riskLevel;

    @Transient
    private String emotionMode;

    @PrePersist
    protected void onCreate() {
        this.timestamp = LocalDateTime.now();
    }
}
