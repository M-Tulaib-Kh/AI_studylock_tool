package com.studylock.service;

import com.studylock.model.*;
import com.studylock.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    public static final int MAX_COURSES_PER_STUDENT = 6;
    public static final int MAX_CREDIT_HOURS_PER_STUDENT = 18;

    private final EnrollmentRepository enrollmentRepository;
    private final TopicProgressRepository topicProgressRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRequestRepository enrollmentRequestRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /**
     * Attempts to enroll a student in a course, applying all StudyLock enrollment rules:
     *  1. Not already enrolled.
     *  2. Max {@link #MAX_COURSES_PER_STUDENT} active enrollments.
     *  3. Max {@link #MAX_CREDIT_HOURS_PER_STUDENT} total credit hours, UNLESS the student
     *     has an admin-approved {@link EnrollmentRequest} (reason CREDIT_LIMIT_EXCEEDED) for
     *     this exact course — in which case the cap is bypassed for that one course.
     *  4. Course semester must be <= student's current semester, OR the student
     *     must have an admin-approved {@link EnrollmentRequest} (reason FUTURE_SEMESTER)
     *     for that course.
     * If either #3 or #4 fails without a matching approval, an
     * {@link EnrollmentApprovalRequiredException} is thrown carrying the specific
     * {@link EnrollmentRequestReason}, so the UI can show the right message and a
     * "Request Approval" action instead of a flat error.
     */
    @Transactional
    public Enrollment enroll(User student, Long courseId) {
        if (enrollmentRepository.existsByStudentIdAndCourseId(student.getId(), courseId)) {
            throw new RuntimeException("Already enrolled in this course.");
        }
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new RuntimeException("Course not found"));

        List<Enrollment> current = enrollmentRepository.findByStudentIdWithCourse(student.getId());
        if (current.size() >= MAX_COURSES_PER_STUDENT) {
            throw new RuntimeException("You can enroll in a maximum of " + MAX_COURSES_PER_STUDENT + " courses per semester.");
        }

        int currentCreditHours = current.stream()
            .mapToInt(e -> e.getCourse().getCreditHours() != null ? e.getCourse().getCreditHours() : 0)
            .sum();
        int courseCredits = course.getCreditHours() != null ? course.getCreditHours() : 0;
        boolean creditsOk = (currentCreditHours + courseCredits) <= MAX_CREDIT_HOURS_PER_STUDENT;
        boolean hasCreditApproval = !creditsOk && enrollmentRequestRepository
            .existsByStudentIdAndCourseIdAndStatusAndReason(student.getId(), courseId,
                EnrollmentRequestStatus.APPROVED, EnrollmentRequestReason.CREDIT_LIMIT_EXCEEDED);

        if (!creditsOk && !hasCreditApproval) {
            throw new EnrollmentApprovalRequiredException(
                "You've already reached the " + MAX_CREDIT_HOURS_PER_STUDENT + "-credit-hour limit for this semester ("
                + currentCreditHours + "/" + MAX_CREDIT_HOURS_PER_STUDENT + " used). Enrolling in \""
                + course.getTitle() + "\" (" + courseCredits + " credit hours) would take you over the limit. "
                + "Send a request below and an admin can approve this course specifically.",
                EnrollmentRequestReason.CREDIT_LIMIT_EXCEEDED);
        }

        boolean semesterOk = isSemesterAllowed(student, course);
        boolean hasSemesterApproval = !semesterOk && enrollmentRequestRepository
            .existsByStudentIdAndCourseIdAndStatusAndReason(student.getId(), courseId,
                EnrollmentRequestStatus.APPROVED, EnrollmentRequestReason.FUTURE_SEMESTER);

        if (!semesterOk && !hasSemesterApproval) {
            throw new EnrollmentApprovalRequiredException(
                "\"" + course.getTitle() + "\" is a semester " + course.getSemester()
                + " course, but you're in semester " + student.getSemester()
                + ". You need admin approval to enroll in a future-semester course.",
                EnrollmentRequestReason.FUTURE_SEMESTER);
        }

        Enrollment e = Enrollment.builder().student(student).course(course).build();
        return enrollmentRepository.save(e);
    }

    /** True if the course's semester is the student's semester or earlier (or either is unset). */
    private boolean isSemesterAllowed(User student, Course course) {
        if (student.getSemester() == null || course.getSemester() == null) return true;
        return course.getSemester() <= student.getSemester();
    }

    /**
     * Raised when a student tries to enroll in a course that needs admin approval —
     * either because it's from a future semester, or because it would push them over
     * the credit-hour cap. The controller catches this distinctly from a plain
     * RuntimeException so the UI can show a "Request Approval" action with the right
     * wording instead of a flat error.
     */
    public static class EnrollmentApprovalRequiredException extends RuntimeException {
        private final EnrollmentRequestReason reason;
        public EnrollmentApprovalRequiredException(String message, EnrollmentRequestReason reason) {
            super(message);
            this.reason = reason;
        }
        public EnrollmentRequestReason getReason() { return reason; }
    }

    @Transactional
    public EnrollmentRequest requestApproval(User student, Long courseId, EnrollmentRequestReason reason) {
        if (enrollmentRepository.existsByStudentIdAndCourseId(student.getId(), courseId)) {
            throw new RuntimeException("Already enrolled in this course.");
        }
        if (enrollmentRequestRepository.existsByStudentIdAndCourseIdAndStatusAndReason(
                student.getId(), courseId, EnrollmentRequestStatus.PENDING, reason)) {
            throw new RuntimeException("You already have a pending approval request for this course.");
        }
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new RuntimeException("Course not found"));

        int currentCreditHours = getCurrentCreditHours(student.getId());

        EnrollmentRequest req = EnrollmentRequest.builder()
            .student(student).course(course)
            .status(EnrollmentRequestStatus.PENDING)
            .reason(reason)
            .studentSemester(student.getSemester())
            .courseSemester(course.getSemester())
            .currentCreditHours(currentCreditHours)
            .requestedCourseCreditHours(course.getCreditHours())
            .build();
        req = enrollmentRequestRepository.save(req);

        String reasonText = reason == EnrollmentRequestReason.CREDIT_LIMIT_EXCEEDED
            ? "would exceed the " + MAX_CREDIT_HOURS_PER_STUDENT + "-credit-hour limit (currently at "
                + currentCreditHours + "/" + MAX_CREDIT_HOURS_PER_STUDENT + ")"
            : "is a future-semester course (Semester " + course.getSemester() + ", student is Semester " + student.getSemester() + ")";

        // Notify all admins so they can review the request
        try {
            userRepository.findByRole(UserRole.ADMIN).forEach(admin -> notificationService.send(
                admin, "Enrollment Approval Needed",
                student.getFullName() + " wants to enroll in \"" + course.getTitle() + "\" — " + reasonText
                    + ". Review in Enrollment Requests.",
                "approval"));
        } catch (Exception ignored) {}

        return req;
    }

    @Transactional
    public Enrollment approveRequest(Long requestId, User admin, String note) {
        EnrollmentRequest req = enrollmentRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Request not found"));
        if (req.getStatus() != EnrollmentRequestStatus.PENDING) {
            throw new RuntimeException("This request has already been " + req.getStatus().toString().toLowerCase() + ".");
        }
        req.setStatus(EnrollmentRequestStatus.APPROVED);
        req.setDecidedBy(admin);
        req.setDecidedAt(LocalDateTime.now());
        req.setAdminNote(note);
        enrollmentRequestRepository.save(req);

        Enrollment enrollment;
        try {
            enrollment = enroll(req.getStudent(), req.getCourse().getId());
        } catch (Exception ex) {
            // Approved, but enrollment cap was hit in the meantime — surface that clearly.
            try {
                notificationService.send(req.getStudent(), "Enrollment Approved (Action Needed)",
                    "Your request for \"" + req.getCourse().getTitle() + "\" was approved, but enrollment failed: "
                        + ex.getMessage(), "approval");
            } catch (Exception ignored) {}
            throw ex;
        }

        try {
            notificationService.send(req.getStudent(), "Enrollment Approved",
                "Your request to enroll in \"" + req.getCourse().getTitle() + "\" has been approved! You are now enrolled.",
                "approval");
        } catch (Exception ignored) {}

        return enrollment;
    }

    @Transactional
    public void rejectRequest(Long requestId, User admin, String note) {
        EnrollmentRequest req = enrollmentRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Request not found"));
        if (req.getStatus() != EnrollmentRequestStatus.PENDING) {
            throw new RuntimeException("This request has already been " + req.getStatus().toString().toLowerCase() + ".");
        }
        req.setStatus(EnrollmentRequestStatus.REJECTED);
        req.setDecidedBy(admin);
        req.setDecidedAt(LocalDateTime.now());
        req.setAdminNote(note);
        enrollmentRequestRepository.save(req);

        try {
            notificationService.send(req.getStudent(), "Enrollment Request Declined",
                "Your request to enroll in \"" + req.getCourse().getTitle() + "\" was declined."
                    + (note != null && !note.isBlank() ? " Reason: " + note : ""), "approval");
        } catch (Exception ignored) {}
    }

    public List<EnrollmentRequest> getPendingRequests() {
        return enrollmentRequestRepository.findPendingWithDetails(EnrollmentRequestStatus.PENDING);
    }

    public long getPendingRequestCount() {
        return enrollmentRequestRepository.countByStatus(EnrollmentRequestStatus.PENDING);
    }

    public List<EnrollmentRequest> getStudentRequests(Long studentId) {
        return enrollmentRequestRepository.findByStudentIdOrderByRequestedAtDesc(studentId);
    }

    /** Total credit hours the student currently carries across active enrollments. */
    public int getCurrentCreditHours(Long studentId) {
        return enrollmentRepository.findByStudentIdWithCourse(studentId).stream()
            .mapToInt(e -> e.getCourse().getCreditHours() != null ? e.getCourse().getCreditHours() : 0)
            .sum();
    }

    @Transactional
    public void unenroll(Long enrollmentId) {
        enrollmentRepository.deleteById(enrollmentId);
    }

    public List<Enrollment> getStudentEnrollments(Long studentId) {
        return enrollmentRepository.findByStudentIdWithCourse(studentId);
    }

    public List<Enrollment> getCourseEnrollments(Long courseId) {
        return enrollmentRepository.findByCourseIdWithStudent(courseId);
    }

    public Optional<Enrollment> findEnrollment(Long studentId, Long courseId) {
        return enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId);
    }

    @Transactional
    public TopicProgress markTopicComplete(User student, Long enrollmentId, String topicName, int timeSpentSeconds) {
        Optional<TopicProgress> existing = topicProgressRepository
            .findByStudentIdAndEnrollmentIdAndTopicName(student.getId(), enrollmentId, topicName);
        if (existing.isPresent()) {
            TopicProgress tp = existing.get();
            tp.setCompleted(true);
            tp.setCompletedAt(LocalDateTime.now());
            tp.setTimeSpentSeconds(tp.getTimeSpentSeconds() + timeSpentSeconds);
            return topicProgressRepository.save(tp);
        }
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        TopicProgress tp = TopicProgress.builder()
            .student(student).enrollment(enrollment).topicName(topicName)
            .completed(true).completedAt(LocalDateTime.now()).timeSpentSeconds(timeSpentSeconds)
            .build();
        return topicProgressRepository.save(tp);
    }

    public List<TopicProgress> getEnrollmentTopics(Long enrollmentId) {
        return topicProgressRepository.findByEnrollmentId(enrollmentId);
    }

    public Map<String, Object> getStudentCourseReport(Long studentId) {
        List<Enrollment> enrollments = enrollmentRepository.findByStudentIdWithCourse(studentId);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalCourses", enrollments.size());
        List<Map<String, Object>> details = new ArrayList<>();
        for (Enrollment e : enrollments) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("enrollment", e);
            List<TopicProgress> topics = topicProgressRepository.findByEnrollmentId(e.getId());
            long completed = topics.stream().filter(TopicProgress::isCompleted).count();
            d.put("completedTopics", completed);
            d.put("totalTopics", topics.size());
            details.add(d);
        }
        report.put("details", details);
        return report;
    }
}
