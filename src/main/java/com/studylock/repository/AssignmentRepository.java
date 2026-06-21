package com.studylock.repository;

import com.studylock.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @Query("SELECT a FROM Assignment a LEFT JOIN FETCH a.course LEFT JOIN FETCH a.createdBy WHERE a.course.id = :courseId ORDER BY a.deadline ASC")
    List<Assignment> findByCourseIdOrderByDeadlineAsc(@Param("courseId") Long courseId);

    @Query("SELECT a FROM Assignment a LEFT JOIN FETCH a.course LEFT JOIN FETCH a.createdBy WHERE a.createdBy.id = :userId ORDER BY a.createdAt DESC")
    List<Assignment> findByCreatedByIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT a FROM Assignment a LEFT JOIN FETCH a.course LEFT JOIN FETCH a.createdBy ORDER BY a.deadline ASC")
    List<Assignment> findAllByOrderByDeadlineAsc();

    @Query("SELECT a FROM Assignment a LEFT JOIN FETCH a.course WHERE a.id IN :ids")
    List<Assignment> findByAssignmentIdIn(@Param("ids") List<Long> ids);
}
