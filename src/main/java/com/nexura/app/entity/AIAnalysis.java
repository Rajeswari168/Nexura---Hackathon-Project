package com.nexura.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_analysis")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel; // 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'

    @Column(name = "key_findings", columnDefinition = "TEXT")
    private String keyFindings;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
