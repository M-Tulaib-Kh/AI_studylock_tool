package com.studylock.repository;

import com.studylock.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.*;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    @Query("SELECT a FROM Attendance a LEFT JOIN FETCH a.student LEFT JOIN FETCH a.course WHERE a.course.id = :courseId AND a.attendanceDate = :date ORDER BY a.student.fullName ASC")
    List<Attendance> findByCourseIdAndDate(@Param("courseId") Long courseId, @Param("date") LocalDate date);

    @Query("SELECT a FROM Attendance a LEFT JOIN FETCH a.course WHERE a.student.id = :studentId ORDER BY a.attendanceDate DESC")
    List<Attendance> findByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT a FROM Attendance a WHERE a.student.id = :studentId AND a.course.id = :courseId ORDER BY a.attendanceDate DESC")
    List<Attendance> findByStudentIdAndCourseId(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    Optional<Attendance> findByStudentIdAndCourseIdAndAttendanceDate(Long studentId, Long courseId, LocalDate date);

    long countByStudentIdAndCourseIdAndStatus(Long studentId, Long courseId, AttendanceStatus status);

    long countByStudentIdAndCourseId(Long studentId, Long courseId);

    @Query("SELECT a FROM Attendance a LEFT JOIN FETCH a.student LEFT JOIN FETCH a.course WHERE a.course.id = :courseId ORDER BY a.attendanceDate DESC, a.student.fullName ASC")
    List<Attendance> findByCourseIdOrderByDate(@Param("courseId") Long courseId);
}
