package com.nexura.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "medications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String dosage;

    @Column(name = "scheduled_time", nullable = false, length = 20)
    private String scheduledTime; // e.g. "08:00"

    @Column(nullable = false, length = 50)
    private String frequency; // e.g. "DAILY"

    private String notes;

    private Boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
