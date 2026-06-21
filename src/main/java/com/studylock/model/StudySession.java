package com.studylock.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "study_sessions")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "planned_duration_seconds", nullable = false)
    private int plannedDurationSeconds;

    @Column(name = "elapsed_seconds")
    @Builder.Default
    private int elapsedSeconds = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @Builder.Default
    private int violations = 0;

    @Column(name = "camera_enabled")
    @Builder.Default
    private boolean cameraEnabled = false;

    @Column(name = "valid_session")
    @Builder.Default
    private boolean validSession = false;

    @Column(name = "blocked_apps", columnDefinition = "TEXT")
    private String blockedApps;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @PrePersist
    protected void onCreate() { startedAt = LocalDateTime.now(); }

    public int getXpEarned() {
        return validSession ? (elapsedSeconds / 60) * 2 : 0;
    }

    public int getEfficiencyPercent() {
        if (violations == 0) return 100;
        return Math.max(0, 100 - (violations * 33));
    }
}
