package com.studylock.service;

import com.studylock.model.*;
import com.studylock.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    /** Lecturer marks attendance for a list of students in a course on a given date */
    @Transactional
    public void markAttendance(Long courseId, LocalDate date, Map<Long, AttendanceStatus> studentStatuses, User markedBy) {
        Course course = courseRepository.findById(courseId).orElseThrow();
        for (Map.Entry<Long, AttendanceStatus> entry : studentStatuses.entrySet()) {
            User student = userRepository.findById(entry.getKey()).orElse(null);
            if (student == null) continue;
            Optional<Attendance> existing = attendanceRepository
                .findByStudentIdAndCourseIdAndAttendanceDate(entry.getKey(), courseId, date);
            if (existing.isPresent()) {
                existing.get().setStatus(entry.getValue());
                existing.get().setMarkedBy(markedBy);
                attendanceRepository.save(existing.get());
            } else {
                attendanceRepository.save(Attendance.builder()
                    .student(student).course(course).markedBy(markedBy)
                    .attendanceDate(date).status(entry.getValue()).build());
            }
        }
    }

    public List<Attendance> getCourseAttendanceForDate(Long courseId, LocalDate date) {
        return attendanceRepository.findByCourseIdAndDate(courseId, date);
    }

    public List<Attendance> getStudentAttendance(Long studentId) {
        return attendanceRepository.findByStudentId(studentId);
    }

    public List<Attendance> getStudentCourseAttendance(Long studentId, Long courseId) {
        return attendanceRepository.findByStudentIdAndCourseId(studentId, courseId);
    }

    public Map<String, Object> getStudentAttendanceSummary(Long studentId, Long courseId) {
        long total   = attendanceRepository.countByStudentIdAndCourseId(studentId, courseId);
        long present = attendanceRepository.countByStudentIdAndCourseIdAndStatus(studentId, courseId, AttendanceStatus.PRESENT);
        long late    = attendanceRepository.countByStudentIdAndCourseIdAndStatus(studentId, courseId, AttendanceStatus.LATE);
        long absent  = total - present - late;
        double pct   = total == 0 ? 0 : ((present + late) * 100.0) / total;
        return Map.of("total", total, "present", present, "late", late, "absent", absent, "percentage", Math.round(pct));
    }

    public List<Attendance> getCourseAllAttendance(Long courseId) {
        return attendanceRepository.findByCourseIdOrderByDate(courseId);
    }
}
