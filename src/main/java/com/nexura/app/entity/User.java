package com.nexura.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    private Integer age;

    @Column(name = "emergency_caregiver_name", length = 100)
    private String emergencyCaregiverName;

    @Column(name = "caregiver_phone_number", length = 20)
    private String caregiverPhoneNumber;

    @Column(name = "ai_provider", length = 50)
    private String aiProvider = "groq";

    @Column(name = "groq_key", length = 255)
    private String groqKey;

    @Column(name = "gemini_key", length = 255)
    private String geminiKey;

    @Column(name = "openai_key", length = 255)
    private String openaiKey;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
