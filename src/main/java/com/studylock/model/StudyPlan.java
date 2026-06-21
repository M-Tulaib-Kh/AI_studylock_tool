package com.studylock.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "study_plans")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class StudyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String planJson; // AI-generated plan stored as JSON

    @Column(columnDefinition = "TEXT")
    private String subject;

    @Column(name = "duration_weeks")
    @Builder.Default
    private int durationWeeks = 4;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
