package com.nexura.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "emergency_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(name = "trigger_reason", nullable = false, columnDefinition = "TEXT")
    private String triggerReason;

    @Column(name = "escalated_at", insertable = false, updatable = false)
    private LocalDateTime escalatedAt;

    @Column(length = 20)
    private String status = "ESCALATED"; // 'ESCALATED', 'RESOLVED'
}
