package com.studylock.repository;

import com.studylock.model.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentId(Long studentId);
    List<Enrollment> findByCourseId(Long courseId);
    Optional<Enrollment> findByStudentIdAndCourseId(Long studentId, Long courseId);
    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);

    @Query("SELECT e FROM Enrollment e JOIN FETCH e.course c LEFT JOIN FETCH c.lecturer WHERE e.student.id = :sid")
    List<Enrollment> findByStudentIdWithCourse(@Param("sid") Long studentId);

    @Query("SELECT e FROM Enrollment e JOIN FETCH e.student WHERE e.course.id = :cid")
    List<Enrollment> findByCourseIdWithStudent(@Param("cid") Long courseId);

    long countByStudentId(Long studentId);
}
