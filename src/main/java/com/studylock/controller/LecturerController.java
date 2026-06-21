package com.studylock.controller;

import com.studylock.model.*;
import com.studylock.repository.SubmissionRepository;
import com.studylock.service.*;
import com.studylock.repository.QuizAttemptRepository;
import com.studylock.repository.UserRepository;
import com.studylock.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/lecturer")
@RequiredArgsConstructor
public class LecturerController {

    private final AcademicService academicService;
    private final OpenRouterService openRouterService;
    private final FileStorageService fileStorageService;
    private final SecurityUtils securityUtils;
    private final NotificationService notificationService;
    private final SubmissionRepository submissionRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final AttendanceService attendanceService;

    @GetMapping("/home")
    public String home(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("courses", academicService.getLecturerCourses(user.getId()));
        model.addAttribute("lectures", academicService.getLecturerLectures(user.getId()));
        model.addAttribute("assignments", academicService.getLecturerAssignments(user.getId()));
        model.addAttribute("submissions", academicService.getLecturerSubmissions(user.getId()));
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        return "lecturer/home";
    }

    @GetMapping("/manage")
    public String manage(Model model, @RequestParam(defaultValue = "lectures") String tab) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("tab", tab);
        model.addAttribute("courses", academicService.getLecturerCourses(user.getId()));
        model.addAttribute("lectures", academicService.getLecturerLectures(user.getId()));
        model.addAttribute("assignments", academicService.getLecturerAssignments(user.getId()));
        model.addAttribute("submissions", academicService.getLecturerSubmissions(user.getId()));
        model.addAttribute("quizzes", academicService.getAllQuizzes().stream()
            .filter(q -> q.getCreatedBy() != null && q.getCreatedBy().getId().equals(user.getId())).toList());
        return "lecturer/manage";
    }

    @PostMapping("/course/create")
    public String createCourse(@RequestParam String title, @RequestParam String description,
                                @RequestParam String subject, RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try { academicService.createCourse(title, description, subject, user); r.addFlashAttribute("success", "Course created!"); }
        catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/lecturer/manage";
    }

    @GetMapping("/create/lecture")
    public String createLecturePage(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("courses", academicService.getLecturerCourses(user.getId()));
        return "lecturer/create_lecture";
    }

    @PostMapping("/create/lecture")
    public String createLecture(@RequestParam Long courseId, @RequestParam String title,
                                 @RequestParam String subject, @RequestParam String description,
                                 @RequestParam("file") MultipartFile file, RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try {
            String fileUrl = "", fileName = "";
            if (file != null && !file.isEmpty()) { fileUrl = fileStorageService.store(file, "lectures"); fileName = file.getOriginalFilename(); }
            academicService.createLecture(courseId, title, subject, description, fileUrl, fileName, user);
            r.addFlashAttribute("success", "Lecture uploaded!");
        } catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/lecturer/manage?tab=lectures";
    }

    @GetMapping("/create/assignment")
    public String createAssignmentPage(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("courses", academicService.getLecturerCourses(user.getId()));
        return "lecturer/create_assignment";
    }

    @PostMapping("/create/assignment")
    public String createAssignment(@RequestParam Long courseId, @RequestParam String title,
                                    @RequestParam String subject, @RequestParam String description,
                                    @RequestParam String instructions, @RequestParam String deadline,
                                    @RequestParam int totalMarks,
                                    @RequestParam(value = "file", required = false) MultipartFile file,
                                    @RequestParam(defaultValue = "false") boolean aiGenerated,
                                    RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try {
            String fileUrl = "", fileName = "";
            if (file != null && !file.isEmpty()) { fileUrl = fileStorageService.store(file, "assignments"); fileName = file.getOriginalFilename(); }
            LocalDateTime dl = LocalDateTime.parse(deadline, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            academicService.createAssignment(courseId, title, subject, description, instructions, dl, totalMarks, user, aiGenerated, fileUrl, fileName);
            r.addFlashAttribute("success", "Assignment created!");
        } catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/lecturer/manage?tab=assignments";
    }

    @PostMapping("/ai/generate-assignment")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> aiGenerateAssignment(@RequestBody Map<String, String> body) {
        try {
            Map<String, String> result = openRouterService.generateAssignment(body.get("subject"), body.get("prompt"));
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) { return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage())); }
    }

    @GetMapping("/create/quiz")
    public String createQuizPage(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("courses", academicService.getLecturerCourses(user.getId()));
        return "lecturer/create_quiz";
    }

    @PostMapping("/ai/generate-quiz")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> aiGenerateQuiz(@RequestBody Map<String, Object> body) {
        try {
            String subject = (String) body.get("subject");
            String prompt = (String) body.get("prompt");
            int count = Integer.parseInt(body.getOrDefault("count", "5").toString());
            String questionsJson = openRouterService.generateQuiz(subject, prompt, count);
            return ResponseEntity.ok(Map.of("success", true, "questionsJson", questionsJson));
        } catch (Exception e) { return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage())); }
    }

    @PostMapping("/create/quiz")
    public String createQuiz(@RequestParam Long courseId, @RequestParam String title,
                              @RequestParam String subject, @RequestParam String description,
                              @RequestParam String questionsJson, @RequestParam int durationMinutes,
                              @RequestParam(defaultValue = "false") boolean aiGenerated,
                              @RequestParam(required = false) MultipartFile quizFile,
                              RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<?> qs = questionsJson != null && !questionsJson.equals("[]")
                ? mapper.readValue(questionsJson, List.class) : List.of();
            int total = qs.stream().mapToInt(q -> {
                try { Map<?,?> qm=(Map<?,?>)q; Object m=qm.get("marks"); return m!=null?Integer.parseInt(m.toString()):2; }
                catch(Exception ex){ return 2; }
            }).sum();
            if(total == 0) total = qs.size() * 2;

            // Handle optional quiz file upload
            String fileUrl = null, fileName = null;
            if(quizFile != null && !quizFile.isEmpty()){
                fileUrl  = fileStorageService.store(quizFile, "quizzes");
                fileName = quizFile.getOriginalFilename();
            }
            academicService.createQuiz(courseId, title, subject, description, questionsJson,
                durationMinutes, Math.max(total, 1), user, aiGenerated, fileUrl, fileName);
            r.addFlashAttribute("success", "Quiz created successfully!");
        } catch (Exception e) { r.addFlashAttribute("error", "Error creating quiz: " + e.getMessage()); }
        return "redirect:/lecturer/manage?tab=quizzes";
    }

    @GetMapping("/grade/{submissionId}")
    public String gradePage(@PathVariable Long submissionId, Model model) {
        Submission sub = submissionRepository.findByIdWithStudent(submissionId).orElseThrow();
        model.addAttribute("user", securityUtils.getCurrentUser());
        model.addAttribute("submission", sub);
        model.addAttribute("assignment", sub.getAssignment());
        return "lecturer/grade_submission";
    }

    @PostMapping("/grade/{submissionId}")
    public String grade(@PathVariable Long submissionId, @RequestParam int marks,
                         @RequestParam String feedback, RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try { academicService.gradeSubmission(submissionId, marks, feedback, user); r.addFlashAttribute("success", "Graded!"); }
        catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/lecturer/manage?tab=submissions";
    }

    @PostMapping("/ai/grade-submission")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> aiGrade(@RequestBody Map<String, Object> body) {
        try {
            String prompt = (String) body.get("assignmentPrompt");
            String notes = (String) body.getOrDefault("notes", "");
            int total = Integer.parseInt(body.getOrDefault("totalMarks", "100").toString());
            return ResponseEntity.ok(Map.of("success", true, "data", openRouterService.gradeSubmission(prompt, notes, total)));
        } catch (Exception e) { return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage())); }
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        model.addAttribute("user", securityUtils.getCurrentUser());
        return "lecturer/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam String fullName,
                                 @RequestParam(required = false) String department,
                                 @RequestParam(required = false) String designation,
                                 RedirectAttributes r) {
        User user = securityUtils.getCurrentUser();
        try {
            userService.updateProfile(user.getId(), fullName, department, null, designation);
            r.addFlashAttribute("success", "Profile updated!");
        } catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/lecturer/profile";
    }

    // ── Student Marks Overview ────────────────────────────────
    @GetMapping("/student-marks")
    public String studentMarks(Model model, @RequestParam(required = false) Long courseId) {
        User user = securityUtils.getCurrentUser();
        List<Course> courses = academicService.getLecturerCourses(user.getId());
        model.addAttribute("user", user);
        model.addAttribute("courses", courses);
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));

        if (courseId != null) {
            model.addAttribute("selectedCourseId", courseId);
            List<Assignment> assignments = academicService.getLecturerAssignments(user.getId()).stream()
                .filter(a -> a.getCourse() != null && a.getCourse().getId().equals(courseId)).toList();
            List<Quiz> quizzes = academicService.getLecturerQuizzes(user.getId()).stream()
                .filter(q -> q.getCourse() != null && q.getCourse().getId().equals(courseId)).toList();
            List<com.studylock.model.User> students = userRepository.findByRole(UserRole.STUDENT);

            // Build marks matrix: student -> assignment/quiz -> marks
            List<Map<String, Object>> studentData = new java.util.ArrayList<>();
            for (com.studylock.model.User s : students) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("student", s);
                // assignment marks
                List<Map<String, Object>> assignMarks = new java.util.ArrayList<>();
                for (Assignment a : assignments) {
                    academicService.getStudentSubmission(a.getId(), s.getId()).ifPresentOrElse(
                        sub -> assignMarks.add(Map.of("title", a.getTitle(), "total", a.getTotalMarks(),
                                "marks", sub.getMarksObtained() != null ? sub.getMarksObtained() : -1,
                                "status", sub.getStatus().name())),
                        () -> assignMarks.add(Map.of("title", a.getTitle(), "total", a.getTotalMarks(),
                                "marks", -1, "status", "NOT_SUBMITTED")));
                }
                // quiz marks
                List<Map<String, Object>> quizMarks = new java.util.ArrayList<>();
                for (Quiz q : quizzes) {
                    academicService.getStudentAttempt(q.getId(), s.getId()).ifPresentOrElse(
                        att -> quizMarks.add(Map.of("title", q.getTitle(), "total", q.getTotalMarks(),
                                "marks", att.getScore(), "status", "ATTEMPTED")),
                        () -> quizMarks.add(Map.of("title", q.getTitle(), "total", q.getTotalMarks(),
                                "marks", -1, "status", "NOT_ATTEMPTED")));
                }
                row.put("assignMarks", assignMarks);
                row.put("quizMarks", quizMarks);
                studentData.add(row);
            }
            model.addAttribute("students", studentData);
            model.addAttribute("assignments", assignments);
            model.addAttribute("quizzes", quizzes);
        }
        return "lecturer/student_marks";
    }

    // ── Lecture summarizer pass-through ──────────────────────
    @PostMapping("/ai/summarize-lecture")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> aiSummarizeLecture(@RequestBody Map<String, String> body) {
        try {
            String title = body.getOrDefault("title", "");
            String desc  = body.getOrDefault("description", "");
            String subj  = body.getOrDefault("subject", "");
            String result = openRouterService.summarizeLecture(title, desc, subj);
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ── Delete lecture / assignment / quiz ────────────────────
    @PostMapping("/lecture/{id}/delete")
    public String deleteLecture(@PathVariable Long id, RedirectAttributes r) {
        try { academicService.deleteLecture(id); r.addFlashAttribute("success", "Lecture deleted"); }
        catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/lecturer/manage?tab=lectures";
    }

    @PostMapping("/assignment/{id}/delete")
    public String deleteAssignment(@PathVariable Long id, RedirectAttributes r) {
        try { academicService.deleteAssignment(id); r.addFlashAttribute("success", "Assignment deleted"); }
        catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/lecturer/manage?tab=assignments";
    }

    @PostMapping("/quiz/{id}/delete")
    public String deleteQuiz(@PathVariable Long id, RedirectAttributes r) {
        try { academicService.deleteQuiz(id); r.addFlashAttribute("success", "Quiz deleted"); }
        catch (Exception e) { r.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/lecturer/manage?tab=quizzes";
    }

    // ── AI Tutor (Lecturer) ───────────────────────────────────
    @GetMapping("/ai-tutor")
    public String aiTutor(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("courses", academicService.getLecturerCourses(user.getId()));
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        return "lecturer/ai_tutor";
    }

    @PostMapping("/ai-tutor/ask")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> askTutor(@RequestBody com.studylock.dto.TutorMessageDto dto) {
        try {
            List<Map<String,Object>> history = dto.getHistory() != null ? dto.getHistory() : new ArrayList<>();
            String answer = openRouterService.askTutor(dto.getSubject(), dto.getMessage(), history);
            return ResponseEntity.ok(Map.of("success", true, "answer", answer));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

}