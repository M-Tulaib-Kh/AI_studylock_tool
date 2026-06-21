package com.studylock.repository;

import com.studylock.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.assignment a LEFT JOIN FETCH a.course WHERE s.assignment.id = :assignmentId AND s.student.id = :studentId")
    Optional<Submission> findByAssignmentIdAndStudentId(@Param("assignmentId") Long assignmentId, @Param("studentId") Long studentId);

    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.assignment a LEFT JOIN FETCH a.course WHERE s.student.id = :studentId ORDER BY s.submittedAt DESC")
    List<Submission> findByStudentIdOrderBySubmittedAtDesc(@Param("studentId") Long studentId);

    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.assignment a LEFT JOIN FETCH a.course WHERE s.assignment.id = :assignmentId")
    List<Submission> findByAssignmentId(@Param("assignmentId") Long assignmentId);

    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.assignment a LEFT JOIN FETCH a.course WHERE s.assignment.id IN :assignmentIds")
    List<Submission> findByAssignmentIdIn(@Param("assignmentIds") List<Long> assignmentIds);

    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.student LEFT JOIN FETCH s.assignment a LEFT JOIN FETCH a.course WHERE s.id = :id")
    Optional<Submission> findByIdWithStudent(@Param("id") Long id);

    long countByStatus(SubmissionStatus status);
}
