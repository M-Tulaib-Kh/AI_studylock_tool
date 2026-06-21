package com.studylock.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "enrollment_requests")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class EnrollmentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EnrollmentRequestStatus status = EnrollmentRequestStatus.PENDING;

    @Column(name = "student_semester")
    private Integer studentSemester;

    @Column(name = "course_semester")
    private Integer courseSemester;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason")
    @Builder.Default
    private EnrollmentRequestReason reason = EnrollmentRequestReason.FUTURE_SEMESTER;

    @Column(name = "current_credit_hours")
    private Integer currentCreditHours;

    @Column(name = "requested_course_credit_hours")
    private Integer requestedCourseCreditHours;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @PrePersist
    protected void onCreate() { requestedAt = LocalDateTime.now(); }
}
