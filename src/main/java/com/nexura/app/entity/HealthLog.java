package com.nexura.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "health_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "blood_pressure", length = 20)
    private String bloodPressure;

    @Column(name = "oxygen_level")
    private Integer oxygenLevel;

    @Column(name = "body_temperature")
    private Double bodyTemperature;

    @Column(name = "stress_level")
    private Integer stressLevel;

    @Column(length = 50)
    private String mood;

    @Column(name = "fatigue_level", length = 50)
    private String fatigueLevel;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
