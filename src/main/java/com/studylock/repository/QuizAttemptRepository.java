package com.studylock.repository;

import com.studylock.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    Optional<QuizAttempt> findByQuizIdAndStudentId(Long quizId, Long studentId);

    List<QuizAttempt> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    // FIX: JOIN FETCH quiz to prevent LazyInitializationException in admin/student_report.html
    @Query("SELECT a FROM QuizAttempt a LEFT JOIN FETCH a.quiz WHERE a.student.id = :studentId ORDER BY a.createdAt DESC")
    List<QuizAttempt> findByStudentIdWithQuizOrderByCreatedAtDesc(@Param("studentId") Long studentId);

    List<QuizAttempt> findByQuizId(Long quizId);

    // FIX: JOIN FETCH quiz to prevent LazyInitializationException in quiz_result.html
    @Query("SELECT a FROM QuizAttempt a LEFT JOIN FETCH a.quiz WHERE a.id = :id")
    Optional<QuizAttempt> findByIdWithQuiz(@Param("id") Long id);
}
