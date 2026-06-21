package com.studylock.controller;

import com.studylock.model.*;
import com.studylock.service.*;
import com.studylock.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final AcademicService academicService;
    private final LoginEventService loginEventService;
    private final SecurityUtils securityUtils;
    private final NotificationService notificationService;
    private final SessionService sessionService;
    private final EnrollmentService enrollmentService;

    private void addCommon(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        model.addAttribute("pendingRequestCount", enrollmentService.getPendingRequestCount());
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        addCommon(model);
        model.addAttribute("userStats", userService.getStats());
        model.addAttribute("totalCourses", academicService.getAllCourses().size());
        model.addAttribute("totalLectures", academicService.getAllLectures().size());
        model.addAttribute("totalAssignments", academicService.getAllAssignments().size());
        model.addAttribute("totalQuizzes", academicService.getAllQuizzes().size());
        model.addAttribute("loginEvents", loginEventService.getRecent());
        model.addAttribute("activeSessions", sessionService.getActiveSessions());
        model.addAttribute("sessionStats", sessionService.getGlobalStats());
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String users(Model model,
                        @RequestParam(defaultValue = "all") String role,
                        @RequestParam(defaultValue = "") String search) {
        addCommon(model);
        model.addAttribute("filterRole", role);
        model.addAttribute("search", search);
        model.addAttribute("userStats", userService.getStats());
        List<User> users = "all".equals(role)
            ? userService.findAll()
            : userService.findByRole(UserRole.valueOf(role.toUpperCase()));
        if (!search.isBlank()) {
            String q = search.toLowerCase();
            users = users.stream().filter(u ->
                u.getFullName().toLowerCase().contains(q) ||
                u.getEmail().toLowerCase().contains(q)).toList();
        }
        model.addAttribute("users", users);
        model.addAttribute("semesterOptionsForUsers", List.of(1,2,3,4,5,6,7,8));
        return "admin/users";
    }

    @PostMapping("/users/create")
    public String createUser(@RequestParam String fullName, @RequestParam String email,
                              @RequestParam String password, @RequestParam String role,
                              @RequestParam(required = false) Integer semester,
                              RedirectAttributes r) {
        try {
            userService.adminCreateUser(fullName, email, password, UserRole.valueOf(role.toUpperCase()), semester);
            r.addFlashAttribute("success", "User created successfully");
        } catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/role")
    public String changeRole(@PathVariable Long id, @RequestParam String role, RedirectAttributes r) {
        try { userService.changeRole(id, UserRole.valueOf(role.toUpperCase())); r.addFlashAttribute("success", "Role updated"); }
        catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable Long id, RedirectAttributes r) {
        try { userService.toggleActive(id); r.addFlashAttribute("success", "User status updated"); }
        catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/semester")
    public String updateUserSemester(@PathVariable Long id, @RequestParam Integer semester, RedirectAttributes r) {
        try { userService.updateSemester(id, semester); r.addFlashAttribute("success", "Semester updated"); }
        catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes r) {
        try { userService.deleteUser(id); r.addFlashAttribute("success", "User deleted"); }
        catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/users";
    }

    // ── Courses (full CRUD + lecturer assign) ─────────────────
    @GetMapping("/courses")
    public String courses(Model model) {
        addCommon(model);
        List<Course> courses = academicService.getAllCourses();
        // Build course details with enrollment counts
        List<Map<String, Object>> courseDetails = new ArrayList<>();
        for (Course c : courses) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("course", c);
            List<Enrollment> enrolled = enrollmentService.getCourseEnrollments(c.getId());
            d.put("studentCount", enrolled.size());
            d.put("students", enrolled.stream().map(e -> e.getStudent()).toList());
            courseDetails.add(d);
        }
        model.addAttribute("courseDetails", courseDetails);
        model.addAttribute("courses", courses);
        model.addAttribute("lecturers", userService.findByRole(UserRole.LECTURER));
        model.addAttribute("totalLectures", academicService.getAllLectures().size());
        model.addAttribute("totalAssignments", academicService.getAllAssignments().size());
        model.addAttribute("totalQuizzes", academicService.getAllQuizzes().size());
        // CS course templates for quick create
        model.addAttribute("csCourseTemplates", List.of(
            "AI & Machine Learning", "Software Engineering", "Data Structures & Algorithms",
            "Web Development", "Game Programming", "Database Management Systems",
            "Operating Systems", "Computer Networks", "Mobile App Development",
            "Cybersecurity", "Cloud Computing", "Computer Graphics"
        ));
        model.addAttribute("semesterOptions", List.of(1,2,3,4,5,6,7,8));
        model.addAttribute("creditHourOptions", List.of(1,2,3,4));
        return "admin/courses";
    }

    @PostMapping("/courses/create")
    public String createCourse(@RequestParam String title,
                                @RequestParam(required = false) String description,
                                @RequestParam(required = false) String subject,
                                @RequestParam(required = false) Long lecturerId,
                                @RequestParam(required = false, defaultValue = "3") Integer creditHours,
                                @RequestParam(required = false, defaultValue = "1") Integer semester,
                                RedirectAttributes r) {
        try {
            User lecturer = null;
            if (lecturerId != null) lecturer = userService.findById(lecturerId);
            academicService.adminCreateCourse(title,
                description != null ? description : "",
                subject != null ? subject : title,
                lecturer, creditHours, semester);
            r.addFlashAttribute("success", "Course \"" + title + "\" created successfully!");
        } catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/courses";
    }

    @PostMapping("/courses/{id}/assign-lecturer")
    public String assignLecturer(@PathVariable Long id,
                                  @RequestParam Long lecturerId,
                                  RedirectAttributes r) {
        try {
            User lecturer = userService.findById(lecturerId);
            academicService.assignLecturer(id, lecturer);
            r.addFlashAttribute("success", "Lecturer assigned successfully!");
        } catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/courses";
    }

    @PostMapping("/courses/{id}/delete")
    public String deleteCourse(@PathVariable Long id, RedirectAttributes r) {
        try { academicService.deleteCourse(id); r.addFlashAttribute("success", "Course deleted"); }
        catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/courses";
    }

    // ── Enrollment Requests (future-semester approval workflow) ─
    @GetMapping("/enrollment-requests")
    public String enrollmentRequests(Model model) {
        addCommon(model);
        model.addAttribute("requests", enrollmentService.getPendingRequests());
        return "admin/enrollment_requests";
    }

    @PostMapping("/enrollment-requests/{id}/approve")
    public String approveEnrollmentRequest(@PathVariable Long id,
                                            @RequestParam(required = false) String note,
                                            RedirectAttributes r) {
        try {
            User admin = securityUtils.getCurrentUser();
            enrollmentService.approveRequest(id, admin, note);
            r.addFlashAttribute("success", "Enrollment request approved — student has been enrolled.");
        } catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/enrollment-requests";
    }

    @PostMapping("/enrollment-requests/{id}/reject")
    public String rejectEnrollmentRequest(@PathVariable Long id,
                                           @RequestParam(required = false) String note,
                                           RedirectAttributes r) {
        try {
            User admin = securityUtils.getCurrentUser();
            enrollmentService.rejectRequest(id, admin, note);
            r.addFlashAttribute("success", "Enrollment request declined.");
        } catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/enrollment-requests";
    }

    // ── Reports (enhanced with student GPA & marks) ──────────
    @GetMapping("/reports")
    public String reports(Model model) {
        addCommon(model);
        try {
            model.addAttribute("loginEvents", loginEventService.getAll());
        } catch (Exception e) { model.addAttribute("loginEvents", List.of()); }
        try {
            model.addAttribute("sessionStats", sessionService.getGlobalStats());
        } catch (Exception e) { model.addAttribute("sessionStats", Map.of()); }
        try {
            model.addAttribute("topStudents", userService.getTopStudents());
        } catch (Exception e) { model.addAttribute("topStudents", List.of()); }
        try {
            model.addAttribute("userStats", userService.getStats());
        } catch (Exception e) { model.addAttribute("userStats", Map.of()); }
        return "admin/reports";
    }

    @GetMapping("/reports/student/{studentId}")
    public String studentReport(@PathVariable Long studentId, Model model) {
        addCommon(model);
        User student = userService.findById(studentId);
        model.addAttribute("student", student);
        model.addAttribute("enrollments", enrollmentService.getStudentEnrollments(studentId));
        model.addAttribute("submissions", academicService.getStudentSubmissions(studentId));
        model.addAttribute("attempts", academicService.getStudentAttempts(studentId));
        model.addAttribute("stats", sessionService.getStudyStats(studentId));
        model.addAttribute("gameState", null); // loaded in service
        // GPA calculation
        List<com.studylock.model.Submission> subs = academicService.getStudentSubmissions(studentId);
        double avgMarks = subs.stream()
            .filter(s -> s.getMarksObtained() != null)
            .mapToInt(s -> s.getMarksObtained())
            .average().orElse(0.0);
        double gpa = Math.min(4.0, avgMarks / 25.0); // 100 marks = 4.0 GPA
        model.addAttribute("gpa", String.format("%.2f", gpa));
        model.addAttribute("avgMarks", String.format("%.1f", avgMarks));
        return "admin/student_report";
    }

    @GetMapping("/lecturer-overview")
    public String lecturerOverview(Model model) {
        addCommon(model);
        List<User> lecturers = userService.findByRole(UserRole.LECTURER);
        List<Map<String, Object>> overview = new ArrayList<>();
        for (User lec : lecturers) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("lecturer", lec);
            List<Course> courses = academicService.getLecturerCourses(lec.getId());
            o.put("courses", courses);
            o.put("courseCount", courses.size());
            int totalStudents = 0;
            List<Map<String,Object>> courseStudents = new ArrayList<>();
            for (Course c : courses) {
                List<Enrollment> enr = enrollmentService.getCourseEnrollments(c.getId());
                totalStudents += enr.size();
                Map<String,Object> cs = new LinkedHashMap<>();
                cs.put("course", c);
                cs.put("students", enr.stream().map(e -> e.getStudent()).toList());
                cs.put("studentCount", enr.size());
                courseStudents.add(cs);
            }
            o.put("totalStudents", totalStudents);
            o.put("courseStudents", courseStudents);
            o.put("lectureCount", academicService.getLecturerLectures(lec.getId()).size());
            overview.add(o);
        }
        model.addAttribute("overview", overview);
        return "admin/lecturer_overview";
    }

    @GetMapping("/notifications")
    public String notificationsPage(Model model) {
        addCommon(model);
        model.addAttribute("lecturers", userService.findByRole(UserRole.LECTURER));
        model.addAttribute("students", userService.findByRole(UserRole.STUDENT));
        return "admin/notifications";
    }

    @PostMapping("/notifications/broadcast")
    public String broadcast(@RequestParam String title, @RequestParam String message,
                             @RequestParam String targetRole, RedirectAttributes r) {
        try {
            List<User> targets = "all".equals(targetRole) ? userService.findAll()
                : userService.findByRole(UserRole.valueOf(targetRole.toUpperCase()));
            targets.forEach(u -> notificationService.send(u, title, message, "info"));
            r.addFlashAttribute("success", "Broadcast sent to " + targets.size() + " users");
        } catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/notifications";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        addCommon(model);
        return "admin/settings";
    }

    @GetMapping("/api/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> data = new HashMap<>();
        data.put("users", userService.getStats());
        data.put("courses", academicService.getAllCourses().size());
        data.put("sessions", sessionService.getGlobalStats());
        return ResponseEntity.ok(data);
    }

    @GetMapping("/api/login-chart")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> loginChart() {
        return ResponseEntity.ok(loginEventService.getDailyStats());
    }
}
