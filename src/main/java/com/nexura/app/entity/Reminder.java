package com.nexura.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "reminders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "scheduled_time", nullable = false)
    private LocalDateTime scheduledTime;

    @Column(name = "actual_time")
    private LocalDateTime actualTime;

    @Column(nullable = false, length = 20)
    private String status; // 'PENDING', 'SNOOZED', 'TAKEN', 'MISSED'

    @Column(name = "delay_minutes")
    private Integer delayMinutes = 0;

    @Column(name = "adaptive_interval_minutes")
    private Integer adaptiveIntervalMinutes = 0;

    @Column(name = "notes_override")
    private String notesOverride;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
