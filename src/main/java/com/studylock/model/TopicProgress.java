package com.studylock.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "topic_progress",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id","enrollment_id","topic_name"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TopicProgress {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Column(name = "topic_name", nullable = false)
    private String topicName;

    @Column(name = "completed")
    @Builder.Default
    private boolean completed = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "time_spent_seconds")
    @Builder.Default
    private int timeSpentSeconds = 0;
}
