package com.studylock.repository;

import com.studylock.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByLecturerId(Long lecturerId);

    // FIX: JOIN FETCH lecturer to prevent LazyInitializationException in Thymeleaf
    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.lecturer ORDER BY c.createdAt DESC")
    List<Course> findAllByOrderByCreatedAtDesc();
}
