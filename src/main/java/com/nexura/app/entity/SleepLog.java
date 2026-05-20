package com.nexura.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "sleep_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SleepLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(name = "sleep_start_time", nullable = false)
    private LocalDateTime sleepStartTime;

    @Column(name = "wake_time", nullable = false)
    private LocalDateTime wakeTime;

    @Column(name = "duration_hours", nullable = false)
    private Double durationHours;

    @Column(name = "sleep_quality")
    private Integer sleepQuality; // 1-5 scale

    @Column(name = "interruptions_count")
    private Integer interruptionsCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
