package com.studylock.service;

import com.studylock.model.*;
import com.studylock.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcademicService {

    private final CourseRepository courseRepository;
    private final LectureRepository lectureRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // ── Courses ───────────────────────────────────────────────
    @Transactional
    public Course createCourse(String title, String description, String subject, User lecturer) {
        Course c = Course.builder().title(title.trim()).description(description)
            .subject(subject).lecturer(lecturer).build();
        return courseRepository.save(c);
    }

    public List<Course> getAllCourses() { return courseRepository.findAllByOrderByCreatedAtDesc(); }
    public List<Course> getLecturerCourses(Long id) { return courseRepository.findByLecturerId(id); }

    @Transactional
    public Course adminCreateCourse(String title, String description, String subject, User lecturer) {
        return adminCreateCourse(title, description, subject, lecturer, 3, 1);
    }

    @Transactional
    public Course adminCreateCourse(String title, String description, String subject, User lecturer,
                                     Integer creditHours, Integer semester) {
        Course c = Course.builder().title(title.trim()).description(description)
            .subject(subject).lecturer(lecturer)
            .creditHours(creditHours != null ? creditHours : 3)
            .semester(semester != null ? semester : 1)
            .build();
        return courseRepository.save(c);
    }

    @Transactional
    public Course assignLecturer(Long courseId, User lecturer) {
        Course c = courseRepository.findById(courseId).orElseThrow(() -> new RuntimeException("Course not found"));
        c.setLecturer(lecturer);
        return courseRepository.save(c);
    }

    @Transactional
    public void deleteCourse(Long id) { courseRepository.deleteById(id); }

    // ── Lectures ──────────────────────────────────────────────
    @Transactional
    public Lecture createLecture(Long courseId, String title, String subject, String description,
                                  String fileUrl, String fileName, User uploader) {
        Course course = courseRepository.findById(courseId).orElseThrow();
        Lecture l = Lecture.builder().course(course).title(title).subject(subject)
            .description(description).fileUrl(fileUrl).fileName(fileName).uploadedBy(uploader).build();
        l = lectureRepository.save(l);
        notifyStudentsNewLecture(l);
        return l;
    }

    public List<Lecture> getAllLectures() { return lectureRepository.findAllByOrderByCreatedAtDesc(); }
    public List<Quiz> getLecturerQuizzes(Long id) { return quizRepository.findByCreatedByIdOrderByCreatedAtDesc(id); }
    public List<Lecture> getLecturerLectures(Long id) { return lectureRepository.findByUploadedByIdOrderByCreatedAtDesc(id); }
    public Lecture getLectureById(Long id) { return lectureRepository.findById(id).orElseThrow(); }

    @Transactional
    public void incrementViews(Long id) { lectureRepository.incrementViews(id); }

    @Transactional
    public void deleteLecture(Long id) { lectureRepository.deleteById(id); }

    // ── Assignments ───────────────────────────────────────────
    @Transactional
    public Assignment createAssignment(Long courseId, String title, String subject, String description,
                                        String instructions, LocalDateTime deadline, int totalMarks,
                                        User creator, boolean aiGenerated, String fileUrl, String fileName) {
        Course course = courseRepository.findById(courseId).orElseThrow();
        Assignment a = Assignment.builder().course(course).title(title).subject(subject)
            .description(description).instructions(instructions).deadline(deadline)
            .totalMarks(totalMarks).createdBy(creator).aiGenerated(aiGenerated)
            .fileUrl(fileUrl != null ? fileUrl : "").fileName(fileName != null ? fileName : "").build();
        a = assignmentRepository.save(a);
        notifyStudentsNewAssignment(a);
        return a;
    }

    public List<Assignment> getAllAssignments() { return assignmentRepository.findAllByOrderByDeadlineAsc(); }
    public List<Assignment> getLecturerAssignments(Long id) { return assignmentRepository.findByCreatedByIdOrderByCreatedAtDesc(id); }
    public Assignment getAssignmentById(Long id) { return assignmentRepository.findById(id).orElseThrow(); }

    @Transactional
    public void deleteAssignment(Long id) { assignmentRepository.deleteById(id); }

    // ── Submissions ───────────────────────────────────────────
    @Transactional
    public Submission submitAssignment(Long assignmentId, User student, String fileUrl, String fileName) {
        Assignment a = assignmentRepository.findById(assignmentId).orElseThrow();
        Optional<Submission> existing = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId());
        if (existing.isPresent()) {
            Submission sub = existing.get();
            sub.setFileUrl(fileUrl); sub.setFileName(fileName);
            sub.setStatus(a.isOverdue() ? SubmissionStatus.LATE : SubmissionStatus.SUBMITTED);
            sub.setSubmittedAt(LocalDateTime.now());
            return submissionRepository.save(sub);
        }
        Submission sub = Submission.builder().assignment(a).student(student).fileUrl(fileUrl)
            .fileName(fileName).status(a.isOverdue() ? SubmissionStatus.LATE : SubmissionStatus.SUBMITTED).build();
        return submissionRepository.save(sub);
    }

    @Transactional
    public Submission gradeSubmission(Long submissionId, int marks, String feedback, User gradedBy) {
        Submission sub = submissionRepository.findById(submissionId).orElseThrow();
        sub.setMarksObtained(marks); sub.setFeedback(feedback); sub.setGradedBy(gradedBy);
        sub.setGradedAt(LocalDateTime.now()); sub.setStatus(SubmissionStatus.GRADED);
        sub = submissionRepository.save(sub);
        notificationService.send(sub.getStudent(), "Assignment Graded",
            "Your submission for '" + sub.getAssignment().getTitle() + "' has been graded. Score: "
            + marks + "/" + sub.getAssignment().getTotalMarks(), "grade");
        return sub;
    }

    public List<Submission> getStudentSubmissions(Long id) { return submissionRepository.findByStudentIdOrderBySubmittedAtDesc(id); }
    public List<Submission> getAssignmentSubmissions(Long id) { return submissionRepository.findByAssignmentId(id); }
    public Optional<Submission> getStudentSubmission(Long aId, Long sId) { return submissionRepository.findByAssignmentIdAndStudentId(aId, sId); }
    public Submission getSubmissionById(Long id) { return submissionRepository.findById(id).orElseThrow(); }

    public List<Submission> getLecturerSubmissions(Long lecturerId) {
        List<Assignment> assignments = assignmentRepository.findByCreatedByIdOrderByCreatedAtDesc(lecturerId);
        if (assignments.isEmpty()) return List.of();
        List<Long> ids = assignments.stream().map(Assignment::getId).collect(Collectors.toList());
        return submissionRepository.findByAssignmentIdIn(ids);
    }

    // ── Quizzes ───────────────────────────────────────────────
    @Transactional
    public Quiz createQuiz(Long courseId, String title, String subject, String description,
                            String questionsJson, int durationMinutes, int totalMarks,
                            User creator, boolean aiGenerated) {
        return createQuiz(courseId, title, subject, description, questionsJson, durationMinutes, totalMarks, creator, aiGenerated, null, null);
    }

    @Transactional
    public Quiz createQuiz(Long courseId, String title, String subject, String description,
                            String questionsJson, int durationMinutes, int totalMarks,
                            User creator, boolean aiGenerated, String fileUrl, String fileName) {
        Course course = courseRepository.findById(courseId).orElseThrow();
        Quiz.QuizBuilder builder = Quiz.builder().course(course).title(title).subject(subject)
            .description(description).questionsJson(questionsJson).durationMinutes(durationMinutes)
            .totalMarks(totalMarks).createdBy(creator).aiGenerated(aiGenerated);
        if (fileUrl != null) builder.fileUrl(fileUrl).fileName(fileName);
        return quizRepository.save(builder.build());
    }

    public List<Quiz> getAllQuizzes() { return quizRepository.findAllByOrderByCreatedAtDesc(); }
    public Quiz getQuizById(Long id) { return quizRepository.findById(id).orElseThrow(); }

    @Transactional
    public void deleteQuiz(Long id) { quizRepository.deleteById(id); }

    @Transactional
    public QuizAttempt submitQuizAttempt(Long quizId, User student, String answersJson, int score) {
        Quiz quiz = quizRepository.findById(quizId).orElseThrow();
        QuizAttempt a = QuizAttempt.builder().quiz(quiz).student(student).answersJson(answersJson)
            .score(score).totalMarks(quiz.getTotalMarks()).build();
        return quizAttemptRepository.save(a);
    }

    public Optional<QuizAttempt> getStudentAttempt(Long quizId, Long studentId) {
        return quizAttemptRepository.findByQuizIdAndStudentId(quizId, studentId);
    }

    public List<QuizAttempt> getStudentAttempts(Long id) { return quizAttemptRepository.findByStudentIdWithQuizOrderByCreatedAtDesc(id); }

    @Transactional
    public void saveAiFeedback(Long attemptId, String feedback) {
        QuizAttempt a = quizAttemptRepository.findById(attemptId).orElseThrow();
        a.setAiFeedback(feedback); a.setAiEvaluated(true);
        quizAttemptRepository.save(a);
    }

    // ── Private helpers ───────────────────────────────────────
    private void notifyStudentsNewLecture(Lecture l) {
        try {
            userRepository.findByRole(UserRole.STUDENT).forEach(s ->
                notificationService.send(s, "New Lecture", "New lecture: " + l.getTitle(), "info"));
        } catch (Exception ignored) {}
    }

    private void notifyStudentsNewAssignment(Assignment a) {
        try {
            userRepository.findByRole(UserRole.STUDENT).forEach(s ->
                notificationService.send(s, "New Assignment", "New assignment: " + a.getTitle() + " | Due: " + a.getDeadline(), "deadline"));
        } catch (Exception ignored) {}
    }
}
