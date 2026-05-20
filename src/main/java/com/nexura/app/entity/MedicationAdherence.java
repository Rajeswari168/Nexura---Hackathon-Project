package com.nexura.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medication_adherence")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedicationAdherence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(nullable = false, length = 20)
    private String status; // 'TAKEN', 'MISSED', 'SNOOZED'

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
