package com.studylock.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_state")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class GameState {

    @Id
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Builder.Default
    private int points = 0;

    @Builder.Default
    private int streak = 0;

    @Column(name = "longest_streak")
    @Builder.Default
    private int longestStreak = 0;

    @Column(name = "last_session_date")
    private LocalDate lastSessionDate;

    @Column(name = "badges_json", columnDefinition = "TEXT")
    private String badgesJson;

    @Column(name = "challenges_json", columnDefinition = "TEXT")
    private String challengesJson;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public int getLevel() { return (points / 500) + 1; }
    public int getNextLevelPoints() { return getLevel() * 500; }
    public double getLevelProgress() { return (points % 500) / 500.0; }
}
