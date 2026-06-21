package com.studylock.repository;

import com.studylock.model.EnrollmentRequest;
import com.studylock.model.EnrollmentRequestReason;
import com.studylock.model.EnrollmentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRequestRepository extends JpaRepository<EnrollmentRequest, Long> {

    List<EnrollmentRequest> findByStatusOrderByRequestedAtDesc(EnrollmentRequestStatus status);

    List<EnrollmentRequest> findByStudentIdOrderByRequestedAtDesc(Long studentId);

    Optional<EnrollmentRequest> findByStudentIdAndCourseIdAndStatus(Long studentId, Long courseId, EnrollmentRequestStatus status);

    boolean existsByStudentIdAndCourseIdAndStatus(Long studentId, Long courseId, EnrollmentRequestStatus status);

    // Reason-aware variant: a student can have one FUTURE_SEMESTER request AND one
    // CREDIT_LIMIT_EXCEEDED request pending/approved for the SAME course at the same time
    // (they're independent rules), so lookups must be scoped by reason too.
    boolean existsByStudentIdAndCourseIdAndStatusAndReason(
        Long studentId, Long courseId, EnrollmentRequestStatus status, EnrollmentRequestReason reason);

    @Query("SELECT r FROM EnrollmentRequest r JOIN FETCH r.student JOIN FETCH r.course WHERE r.status = :status ORDER BY r.requestedAt DESC")
    List<EnrollmentRequest> findPendingWithDetails(@Param("status") EnrollmentRequestStatus status);

    long countByStatus(EnrollmentRequestStatus status);
}
