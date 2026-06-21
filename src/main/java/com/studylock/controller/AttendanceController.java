package com.studylock.controller;

import com.studylock.model.*;
import com.studylock.repository.*;
import com.studylock.service.*;
import com.studylock.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/lecturer/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AcademicService academicService;
    private final SecurityUtils securityUtils;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @GetMapping
    public String attendancePage(Model model,
                                  @RequestParam(required = false) Long courseId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        User user = securityUtils.getCurrentUser();
        if (date == null) date = LocalDate.now();
        List<Course> courses = academicService.getLecturerCourses(user.getId());
        model.addAttribute("user", user);
        model.addAttribute("courses", courses);
        model.addAttribute("selectedDate", date);
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));

        if (courseId != null) {
            model.addAttribute("selectedCourseId", courseId);
            List<User> students = userRepository.findByRole(UserRole.STUDENT);
            List<Attendance> existing = attendanceService.getCourseAttendanceForDate(courseId, date);
            Map<Long, AttendanceStatus> existingMap = new HashMap<>();
            for (Attendance a : existing) existingMap.put(a.getStudent().getId(), a.getStatus());
            model.addAttribute("students", students);
            model.addAttribute("existingMap", existingMap);
        }
        return "lecturer/attendance";
    }

    @PostMapping("/mark")
    public String markAttendance(@RequestParam Long courseId,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @RequestParam Map<String, String> params,
                                  RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        Map<Long, AttendanceStatus> statuses = new HashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getKey().startsWith("status_")) {
                try {
                    Long sid = Long.parseLong(e.getKey().substring(7));
                    statuses.put(sid, AttendanceStatus.valueOf(e.getValue()));
                } catch (Exception ignored) {}
            }
        }
        try {
            attendanceService.markAttendance(courseId, date, statuses, user);
            r.addFlashAttribute("success", "Attendance marked for " + statuses.size() + " students");
        } catch (Exception e) {
            r.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/lecturer/attendance?courseId=" + courseId + "&date=" + date;
    }
}
