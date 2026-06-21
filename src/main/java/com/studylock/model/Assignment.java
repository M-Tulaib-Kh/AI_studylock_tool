package com.studylock.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "assignments")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(nullable = false)
    private String title;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "file_name")
    private String fileName;

    private LocalDateTime deadline;

    @Column(name = "total_marks")
    @Builder.Default
    private int totalMarks = 100;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "ai_generated")
    @Builder.Default
    private boolean aiGenerated = false;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL)
    private List<Submission> submissions;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public boolean isOverdue() {
        return deadline != null && LocalDateTime.now().isAfter(deadline);
    }

    public long getDaysUntilDeadline() {
        if (deadline == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), deadline);
    }
}
