package com.studylock.repository;

import com.studylock.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface LectureRepository extends JpaRepository<Lecture, Long> {

    @Query("SELECT l FROM Lecture l LEFT JOIN FETCH l.course LEFT JOIN FETCH l.uploadedBy WHERE l.course.id = :courseId ORDER BY l.createdAt DESC")
    List<Lecture> findByCourseIdOrderByCreatedAtDesc(@Param("courseId") Long courseId);

    @Query("SELECT l FROM Lecture l LEFT JOIN FETCH l.course LEFT JOIN FETCH l.uploadedBy WHERE l.uploadedBy.id = :userId ORDER BY l.createdAt DESC")
    List<Lecture> findByUploadedByIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT l FROM Lecture l LEFT JOIN FETCH l.course LEFT JOIN FETCH l.uploadedBy ORDER BY l.createdAt DESC")
    List<Lecture> findAllByOrderByCreatedAtDesc();

    @Modifying
    @Query("UPDATE Lecture l SET l.views = l.views + 1 WHERE l.id = :id")
    void incrementViews(@Param("id") Long id);
}
