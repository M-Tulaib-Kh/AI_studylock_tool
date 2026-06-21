package com.studylock.controller;

import com.studylock.dto.TutorMessageDto;
import com.studylock.model.*;
import com.studylock.repository.GameStateRepository;
import com.studylock.repository.QuizAttemptRepository;
import com.studylock.service.*;
import com.studylock.repository.UserRepository;
import com.studylock.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;
import java.util.function.Predicate;

@Controller
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentController {

    private final AcademicService academicService;
    private final SessionService sessionService;
    private final NotificationService notificationService;
    private final GameStateRepository gameStateRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final SecurityUtils securityUtils;
    private final FileStorageService fileStorageService;
    private final UserService userService;
    private final StudyPlanService studyPlanService;
    private final AttendanceService attendanceService;
    private final UserRepository userRepository;
    private final OpenRouterService openRouterService;
    private final EnrollmentService enrollmentService;

    @GetMapping("/home")
    public String home(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("lectures", academicService.getAllLectures().stream().limit(5).toList());
        model.addAttribute("assignments", academicService.getAllAssignments().stream().limit(5).toList());
        model.addAttribute("submissions", academicService.getStudentSubmissions(user.getId()));
        model.addAttribute("attempts", academicService.getStudentAttempts(user.getId()));
        model.addAttribute("stats", sessionService.getStudyStats(user.getId()));
        model.addAttribute("gameState", gameStateRepository.findByUserId(user.getId()).orElse(null));
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        model.addAttribute("enrollments", enrollmentService.getStudentEnrollments(user.getId()));
        return "student/home";
    }

    @GetMapping("/academic")
    public String academic(Model model,
                            @RequestParam(defaultValue = "lectures") String tab,
                            @RequestParam(defaultValue = "") String search) {
        User user = securityUtils.getCurrentUser();
        List<Lecture> lectures = academicService.getAllLectures();
        List<Assignment> assignments = academicService.getAllAssignments();
        List<Quiz> quizzes = academicService.getAllQuizzes();
        if (!search.isEmpty()) {
            String q = search.toLowerCase();
            lectures   = lectures.stream().filter(l -> l.getTitle().toLowerCase().contains(q) || l.getSubject().toLowerCase().contains(q)).toList();
            assignments= assignments.stream().filter(a -> a.getTitle().toLowerCase().contains(q)).toList();
            quizzes    = quizzes.stream().filter(qz -> qz.getTitle().toLowerCase().contains(q)).toList();
        }
        model.addAttribute("user", user);
        model.addAttribute("tab", tab);
        model.addAttribute("search", search);
        model.addAttribute("lectures", lectures);
        model.addAttribute("assignments", assignments);
        model.addAttribute("quizzes", quizzes);
        model.addAttribute("submissions", academicService.getStudentSubmissions(user.getId()));
        model.addAttribute("attempts", academicService.getStudentAttempts(user.getId()));
        return "student/academic";
    }

    @GetMapping("/lecture/{id}")
    public String lectureDetail(@PathVariable Long id, Model model) {
        academicService.incrementViews(id);
        model.addAttribute("user", securityUtils.getCurrentUser());
        model.addAttribute("lecture", academicService.getLectureById(id));
        return "student/lecture_detail";
    }

    @GetMapping("/assignment/{id}")
    public String assignmentDetail(@PathVariable Long id, Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("assignment", academicService.getAssignmentById(id));
        model.addAttribute("submission", academicService.getStudentSubmission(id, user.getId()).orElse(null));
        return "student/assignment_detail";
    }

    @PostMapping("/assignment/{id}/submit")
    public String submitAssignment(@PathVariable Long id,
                                    @RequestParam("file") MultipartFile file,
                                    RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try {
            String fileUrl = fileStorageService.store(file, "submissions");
            academicService.submitAssignment(id, user, fileUrl, file.getOriginalFilename());
            r.addFlashAttribute("success", "Assignment submitted successfully!");
        } catch (Exception e) {
            r.addFlashAttribute("error", "Upload failed: " + e.getMessage());
        }
        return "redirect:/student/assignment/" + id;
    }

    @GetMapping("/quiz/{id}")
    public String quizAttempt(@PathVariable Long id, Model model) {
        User user = securityUtils.getCurrentUser();
        Quiz quiz = academicService.getQuizById(id);
        Optional<QuizAttempt> existing = academicService.getStudentAttempt(id, user.getId());
        if (existing.isPresent()) return "redirect:/student/quiz-result/" + existing.get().getId();
        model.addAttribute("user", user);
        model.addAttribute("quiz", quiz);
        model.addAttribute("questionsJson", quiz.getQuestionsJson() != null ? quiz.getQuestionsJson() : "[]");
        return "student/quiz_attempt";
    }

    @PostMapping("/quiz/{id}/submit")
    public String submitQuiz(@PathVariable Long id,
                              @RequestParam Map<String, String> params,
                              RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try {
            Quiz quiz = academicService.getQuizById(id);
            ObjectMapper mapper = new ObjectMapper();
            List<com.studylock.dto.QuizQuestionDto> questions = mapper.readValue(
                quiz.getQuestionsJson() != null ? quiz.getQuestionsJson() : "[]",
                mapper.getTypeFactory().constructCollectionType(List.class, com.studylock.dto.QuizQuestionDto.class));
            Map<String, String> answers = new HashMap<>();
            int score = 0;
            for (com.studylock.dto.QuizQuestionDto q : questions) {
                String ans = params.get("answer_" + q.getId());
                if (ans != null) {
                    answers.put(q.getId(), ans);
                    if (ans.equals(q.getCorrectAnswer())) score += q.getMarks();
                }
            }
            String answersJson = mapper.writeValueAsString(answers);
            QuizAttempt attempt = academicService.submitQuizAttempt(id, user, answersJson, score);
            return "redirect:/student/quiz-result/" + attempt.getId();
        } catch (Exception e) {
            r.addFlashAttribute("error", "Submission failed: " + e.getMessage());
            return "redirect:/student/quiz/" + id;
        }
    }

    @PostMapping("/quiz/{id}/submit-file")
    public String submitQuizFile(@PathVariable Long id,
                                  @RequestParam("file") MultipartFile file,
                                  RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try {
            Quiz quiz = academicService.getQuizById(id);
            Optional<QuizAttempt> existing = academicService.getStudentAttempt(id, user.getId());
            if (existing.isPresent()) { r.addFlashAttribute("error", "Already submitted"); return "redirect:/student/academic?tab=quizzes"; }
            String fileUrl = fileStorageService.store(file, "quiz_answers");
            QuizAttempt attempt = academicService.submitQuizAttempt(id, user, "{\"file\":\"" + fileUrl + "\"}", 0);
            notificationService.send(user, "Quiz Submitted", "Your answer for '"+quiz.getTitle()+"' has been submitted for review.", "info");
            r.addFlashAttribute("success", "Quiz answer submitted! Awaiting lecturer review.");
            return "redirect:/student/quiz-result/" + attempt.getId();
        } catch (Exception e) {
            r.addFlashAttribute("error", e.getMessage());
            return "redirect:/student/quiz/" + id;
        }
    }

    @GetMapping("/quiz-result/{attemptId}")
    public String quizResult(@PathVariable Long attemptId, Model model) {
        User user = securityUtils.getCurrentUser();
        QuizAttempt attempt = quizAttemptRepository.findByIdWithQuiz(attemptId).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        model.addAttribute("attempt", attempt);
        model.addAttribute("quiz", attempt.getQuiz());
        return "student/quiz_result";
    }

    @GetMapping("/rewards")
    public String rewards(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("gameState", gameStateRepository.findByUserId(user.getId()).orElse(null));
        model.addAttribute("stats", sessionService.getStudyStats(user.getId()));
        return "student/rewards";
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("gameState", gameStateRepository.findByUserId(user.getId()).orElse(null));
        model.addAttribute("stats", sessionService.getStudyStats(user.getId()));
        return "student/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam String fullName,
                                 @RequestParam(required = false) String department,
                                 @RequestParam(required = false) String registrationNo,
                                 @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                                 RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try {
            if (avatar != null && !avatar.isEmpty()) {
                String url = fileStorageService.store(avatar, "avatars");
                userService.updateAvatar(user.getId(), url);
            }
            userService.updateProfile(user.getId(), fullName, department, registrationNo, null);
            r.addFlashAttribute("success", "Profile updated!");
        } catch (Exception e) {
            r.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/profile";
    }

    @GetMapping("/notifications")
    public String notifications(Model model) {
        User user = securityUtils.getCurrentUser();
        notificationService.markAllRead(user.getId());
        model.addAttribute("user", user);
        model.addAttribute("notifications", notificationService.getUserNotifications(user.getId()));
        return "student/notifications";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@RequestParam String oldPassword,
                                  @RequestParam String newPassword,
                                  RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try {
            userService.changePassword(user.getId(), oldPassword, newPassword);
            r.addFlashAttribute("success", "Password changed successfully");
        } catch (Exception e) {
            r.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/profile";
    }

    @PostMapping("/notifications/read-all")
    public String markAllRead(RedirectAttributes r) {
        notificationService.markAllRead(securityUtils.getCurrentUser().getId());
        return "redirect:/student/notifications";
    }

    // ── Study Plan ────────────────────────────────────────────
    @GetMapping("/study-plan")
    public String studyPlan(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("plans", studyPlanService.getStudentPlans(user.getId()));
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        return "student/study_plan";
    }

    @PostMapping("/study-plan/generate")
    public String generateStudyPlan(@RequestParam String subject,
                                     @RequestParam String goals,
                                     @RequestParam(defaultValue = "4") int weeks,
                                     RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try {
            studyPlanService.generateAndSave(user, subject, goals, weeks);
            r.addFlashAttribute("success", "Study plan generated successfully!");
        } catch (Exception e) {
            r.addFlashAttribute("error", "AI error: " + e.getMessage());
        }
        return "redirect:/student/study-plan";
    }

    @PostMapping("/study-plan/{id}/delete")
    public String deleteStudyPlan(@PathVariable Long id, RedirectAttributes r) {
        try { studyPlanService.delete(id); r.addFlashAttribute("success", "Plan deleted"); }
        catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/student/study-plan";
    }

    // ── Leaderboard ───────────────────────────────────────────
    @GetMapping("/leaderboard")
    public String leaderboard(Model model) {
        User user = securityUtils.getCurrentUser();
        List<com.studylock.model.GameState> allStates = userRepository.findByRole(UserRole.STUDENT).stream()
            .map(s -> gameStateRepository.findByUserId(s.getId()).orElse(null))
            .filter(gs -> gs != null)
            .sorted((a, b) -> Integer.compare(b.getPoints(), a.getPoints()))
            .toList();
        model.addAttribute("user", user);
        model.addAttribute("leaderboard", allStates);
        model.addAttribute("myState", gameStateRepository.findByUserId(user.getId()).orElse(null));
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        return "student/leaderboard";
    }

    // ── Attendance ────────────────────────────────────────────
    @GetMapping("/attendance")
    public String attendance(Model model) {
        User user = securityUtils.getCurrentUser();
        List<com.studylock.model.Attendance> records = attendanceService.getStudentAttendance(user.getId());
        java.util.Set<Long> courseIds = new java.util.LinkedHashSet<>();
        records.forEach(rec -> courseIds.add(rec.getCourse().getId()));
        List<Map<String, Object>> summary = new java.util.ArrayList<>();
        for (Long cid : courseIds) {
            Map<String, Object> sum = new java.util.HashMap<>(attendanceService.getStudentAttendanceSummary(user.getId(), cid));
            records.stream().filter(rec -> rec.getCourse().getId().equals(cid)).findFirst()
                .ifPresent(rec -> sum.put("course", rec.getCourse()));
            summary.add(sum);
        }
        model.addAttribute("user", user);
        model.addAttribute("records", records);
        model.addAttribute("summary", summary);
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        return "student/attendance";
    }

    // ── AI Lecture Summarizer ─────────────────────────────────
    @PostMapping("/lecture/{id}/summarize")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> summarizeLecture(@PathVariable Long id) {
        try {
            com.studylock.model.Lecture lec = academicService.getLectureById(id);
            String json = openRouterService.summarizeLecture(lec.getTitle(), lec.getDescription(), lec.getSubject());
            return ResponseEntity.ok(Map.of("success", true, "data", json));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ── AI Tutor ──────────────────────────────────────────────
    @GetMapping("/ai-tutor")
    public String aiTutor(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("enrollments", enrollmentService.getStudentEnrollments(user.getId()));
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        return "student/ai_tutor";
    }

    @PostMapping("/ai-tutor/ask")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> askTutor(@RequestBody TutorMessageDto dto) {
        try {
            List<Map<String,Object>> history = dto.getHistory() != null ? dto.getHistory() : new ArrayList<>();
            String answer = openRouterService.askTutor(dto.getSubject(), dto.getMessage(), history);
            return ResponseEntity.ok(Map.of("success", true, "answer", answer));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ── Enrollments ───────────────────────────────────────────
    @GetMapping("/enrollments")
    public String enrollments(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        java.util.List<com.studylock.model.Enrollment> enrollments = enrollmentService.getStudentEnrollments(user.getId());
        model.addAttribute("enrollments", enrollments);
        model.addAttribute("allCourses", academicService.getAllCourses());
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        java.util.Set<Long> enrolledCourseIds = enrollments.stream()
            .map(e -> e.getCourse().getId())
            .collect(java.util.stream.Collectors.toSet());
        model.addAttribute("enrolledCourseIds", enrolledCourseIds);

        int currentCreditHours = enrollmentService.getCurrentCreditHours(user.getId());
        model.addAttribute("currentCreditHours", currentCreditHours);
        model.addAttribute("maxCreditHours", com.studylock.service.EnrollmentService.MAX_CREDIT_HOURS_PER_STUDENT);
        model.addAttribute("maxCourses", com.studylock.service.EnrollmentService.MAX_COURSES_PER_STUDENT);

        java.util.List<com.studylock.model.EnrollmentRequest> myRequests = enrollmentService.getStudentRequests(user.getId());
        // Track pending requests separately per reason: a course can simultaneously need
        // both a semester-approval AND a credit-limit-approval, and they're independent.
        java.util.Set<Long> pendingSemesterCourseIds = myRequests.stream()
            .filter(req -> req.getStatus() == com.studylock.model.EnrollmentRequestStatus.PENDING)
            .filter(req -> req.getReason() == com.studylock.model.EnrollmentRequestReason.FUTURE_SEMESTER)
            .map(req -> req.getCourse().getId())
            .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> pendingCreditCourseIds = myRequests.stream()
            .filter(req -> req.getStatus() == com.studylock.model.EnrollmentRequestStatus.PENDING)
            .filter(req -> req.getReason() == com.studylock.model.EnrollmentRequestReason.CREDIT_LIMIT_EXCEEDED)
            .map(req -> req.getCourse().getId())
            .collect(java.util.stream.Collectors.toSet());
        model.addAttribute("pendingRequestCourseIds", pendingSemesterCourseIds);
        model.addAttribute("pendingCreditCourseIds", pendingCreditCourseIds);

        return "student/enrollments";
    }

    @PostMapping("/enroll/{courseId}")
    public String enroll(@PathVariable Long courseId, RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try {
            enrollmentService.enroll(user, courseId);
            r.addFlashAttribute("success", "Enrolled successfully!");
        } catch (com.studylock.service.EnrollmentService.EnrollmentApprovalRequiredException e) {
            r.addFlashAttribute("approvalNeededCourseId", courseId);
            r.addFlashAttribute("approvalNeededReason", e.getReason().name());
            r.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            r.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/enrollments";
    }

    @PostMapping("/enrollments/request-approval/{courseId}")
    public String requestEnrollmentApproval(@PathVariable Long courseId,
                                             @RequestParam(defaultValue = "FUTURE_SEMESTER") String reason,
                                             RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try {
            com.studylock.model.EnrollmentRequestReason parsedReason =
                com.studylock.model.EnrollmentRequestReason.valueOf(reason);
            enrollmentService.requestApproval(user, courseId, parsedReason);
            r.addFlashAttribute("success", "Approval request sent to the admin. You'll be notified once it's reviewed.");
        } catch (Exception e) {
            r.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/enrollments";
    }

    @PostMapping("/unenroll/{enrollmentId}")
    public String unenroll(@PathVariable Long enrollmentId, RedirectAttributes r) {
        try {
            enrollmentService.unenroll(enrollmentId);
            r.addFlashAttribute("success", "Unenrolled from course.");
        } catch (Exception e) {
            r.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/enrollments";
    }

    // ── Topic Session (timer-based study) ─────────────────────
    @GetMapping("/topic-session")
    public String topicSession(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("enrollments", enrollmentService.getStudentEnrollments(user.getId()));
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        return "student/topic_session";
    }

    @PostMapping("/topic-session/complete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> completeTopicSession(
            @RequestBody Map<String, Object> payload) {
        User user = securityUtils.getCurrentUser();
        try {
            Long enrollmentId = Long.valueOf(payload.get("enrollmentId").toString());
            String topicName = payload.get("topicName").toString();
            int timeSpent = Integer.parseInt(payload.get("timeSpentSeconds").toString());
            TopicProgress tp = enrollmentService.markTopicComplete(user, enrollmentId, topicName, timeSpent);
            return ResponseEntity.ok(Map.of("success", true, "topicId", tp.getId()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/topic-session/topics/{enrollmentId}")
    @ResponseBody
    public ResponseEntity<List<TopicProgress>> getTopics(@PathVariable Long enrollmentId) {
        return ResponseEntity.ok(enrollmentService.getEnrollmentTopics(enrollmentId));
    }
}
