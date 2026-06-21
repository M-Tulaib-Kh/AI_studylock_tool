package com.studylock.repository;

import com.studylock.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    @Query("SELECT q FROM Quiz q LEFT JOIN FETCH q.course LEFT JOIN FETCH q.createdBy WHERE q.course.id = :courseId ORDER BY q.createdAt DESC")
    List<Quiz> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    @Query("SELECT q FROM Quiz q LEFT JOIN FETCH q.course LEFT JOIN FETCH q.createdBy WHERE q.createdBy.id = :userId ORDER BY q.createdAt DESC")
    List<Quiz> findByCreatedByIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT q FROM Quiz q LEFT JOIN FETCH q.course LEFT JOIN FETCH q.createdBy ORDER BY q.createdAt DESC")
    List<Quiz> findAllByOrderByCreatedAtDesc();
}
