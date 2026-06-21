package com.studylock.service;

import com.studylock.model.*;
import com.studylock.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private TopicProgressRepository topicProgressRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentRequestRepository enrollmentRequestRepository;
    @Mock private NotificationService notificationService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private User student;
    private Course course;

    @BeforeEach
    void setUp() {
        student = User.builder().id(1L).email("student@studylock.com")
                .fullName("Test Student").role(UserRole.STUDENT).active(true)
                .semester(4).build();
        course = Course.builder().id(10L).title("Data Structures & Algorithms")
                .description("Core CS course").subject("DSA")
                .creditHours(4).semester(3).build();
    }

    private Enrollment enrollmentOf(Course c) {
        return Enrollment.builder().id(c.getId()).student(student).course(c).build();
    }

    // ── Basic enroll ──────────────────────────────────────────
    @Test
    void enroll_createsNewEnrollment_whenWithinAllLimitsAndSemesterAllowed() {
        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(false);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(List.of());
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        Enrollment result = enrollmentService.enroll(student, 10L);

        assertNotNull(result);
        assertEquals(student, result.getStudent());
        assertEquals(course, result.getCourse());
    }

    @Test
    void enroll_throws_whenAlreadyEnrolled() {
        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> enrollmentService.enroll(student, 10L));

        assertEquals("Already enrolled in this course.", ex.getMessage());
        verify(courseRepository, never()).findById(any());
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void enroll_throws_whenCourseNotFound() {
        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 99L)).thenReturn(false);
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> enrollmentService.enroll(student, 99L));

        assertEquals("Course not found", ex.getMessage());
    }

    // ── Max 6 courses rule ────────────────────────────────────
    @Test
    void enroll_throws_whenAlreadyAtSixCourses() {
        List<Enrollment> sixCourses = new ArrayList<>();
        for (long i = 1; i <= 6; i++) {
            Course c = Course.builder().id(i).title("Course " + i).creditHours(1).semester(1).build();
            sixCourses.add(Enrollment.builder().id(i).student(student).course(c).build());
        }
        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(false);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(sixCourses);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> enrollmentService.enroll(student, 10L));

        assertTrue(ex.getMessage().contains("maximum of 6 courses"));
        verify(enrollmentRepository, never()).save(any());
    }

    // ── Max 18 credit-hours rule ──────────────────────────────
    @Test
    void enroll_throwsApprovalRequired_whenCreditHoursWouldExceed18AndNoApproval() {
        Course bigCourse = Course.builder().id(20L).title("Heavy Course").creditHours(16).semester(1).build();
        Enrollment existing = Enrollment.builder().id(1L).student(student).course(bigCourse).build();

        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(false);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course)); // 4 credit hours
        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(List.of(existing)); // 16 already
        when(enrollmentRequestRepository.existsByStudentIdAndCourseIdAndStatusAndReason(
                1L, 10L, EnrollmentRequestStatus.APPROVED, EnrollmentRequestReason.CREDIT_LIMIT_EXCEEDED))
            .thenReturn(false);

        var ex = assertThrows(EnrollmentService.EnrollmentApprovalRequiredException.class,
                () -> enrollmentService.enroll(student, 10L));

        assertEquals(EnrollmentRequestReason.CREDIT_LIMIT_EXCEEDED, ex.getReason());
        assertTrue(ex.getMessage().contains("18"));
        assertTrue(ex.getMessage().contains("admin"));
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void enroll_succeeds_whenCreditHoursWouldExceed18_butHasApprovedRequestForThatCourse() {
        Course bigCourse = Course.builder().id(20L).title("Heavy Course").creditHours(16).semester(1).build();
        Enrollment existing = Enrollment.builder().id(1L).student(student).course(bigCourse).build();

        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(false);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course)); // 4 credit hours -> total 20, over cap
        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(List.of(existing));
        when(enrollmentRequestRepository.existsByStudentIdAndCourseIdAndStatusAndReason(
                1L, 10L, EnrollmentRequestStatus.APPROVED, EnrollmentRequestReason.CREDIT_LIMIT_EXCEEDED))
            .thenReturn(true);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> enrollmentService.enroll(student, 10L));
    }

    @Test
    void enroll_succeeds_whenExactlyAt18CreditHours() {
        Course existingCourse = Course.builder().id(21L).title("Other Course").creditHours(14).semester(1).build();
        Enrollment existing = Enrollment.builder().id(1L).student(student).course(existingCourse).build();

        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(false);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course)); // 4 credit hours -> total 18
        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(List.of(existing));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> enrollmentService.enroll(student, 10L));
        // Exactly at the cap is NOT exceeding it, so the credit-approval lookup must never run.
        verify(enrollmentRequestRepository, never()).existsByStudentIdAndCourseIdAndStatusAndReason(
            any(), any(), any(), eq(EnrollmentRequestReason.CREDIT_LIMIT_EXCEEDED));
    }

    // ── Semester gating ───────────────────────────────────────
    @Test
    void enroll_succeeds_whenCourseSemesterIsEqualToStudentSemester() {
        course.setSemester(4); // student is also semester 4
        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(false);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(List.of());
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> enrollmentService.enroll(student, 10L));
        // Within both the credit cap and own semester, so NEITHER approval lookup should run.
        verify(enrollmentRequestRepository, never()).existsByStudentIdAndCourseIdAndStatusAndReason(any(), any(), any(), any());
    }

    @Test
    void enroll_throwsApprovalRequired_whenCourseIsFutureSemesterAndNoApproval() {
        course.setSemester(5); // student is semester 4 -> future semester
        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(false);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(List.of());
        when(enrollmentRequestRepository.existsByStudentIdAndCourseIdAndStatusAndReason(
                1L, 10L, EnrollmentRequestStatus.APPROVED, EnrollmentRequestReason.FUTURE_SEMESTER))
            .thenReturn(false);

        var ex = assertThrows(EnrollmentService.EnrollmentApprovalRequiredException.class,
                () -> enrollmentService.enroll(student, 10L));

        assertEquals(EnrollmentRequestReason.FUTURE_SEMESTER, ex.getReason());
        assertTrue(ex.getMessage().contains("semester 5"));
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void enroll_succeeds_whenFutureSemester_butHasApprovedRequest() {
        course.setSemester(5);
        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(false);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(List.of());
        when(enrollmentRequestRepository.existsByStudentIdAndCourseIdAndStatusAndReason(
                1L, 10L, EnrollmentRequestStatus.APPROVED, EnrollmentRequestReason.FUTURE_SEMESTER))
            .thenReturn(true);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> enrollmentService.enroll(student, 10L));
    }

    // ── Approval workflow ─────────────────────────────────────
    @Test
    void requestApproval_createsPendingRequest_withFutureSemesterReason_andNotifiesAdmins() {
        course.setSemester(5);
        User admin = User.builder().id(99L).email("admin@studylock.com").fullName("Admin").role(UserRole.ADMIN).build();

        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(false);
        when(enrollmentRequestRepository.existsByStudentIdAndCourseIdAndStatusAndReason(
                1L, 10L, EnrollmentRequestStatus.PENDING, EnrollmentRequestReason.FUTURE_SEMESTER)).thenReturn(false);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(List.of());
        when(enrollmentRequestRepository.save(any(EnrollmentRequest.class))).thenAnswer(inv -> {
            EnrollmentRequest req = inv.getArgument(0);
            req.setId(500L);
            return req;
        });
        when(userRepository.findByRole(UserRole.ADMIN)).thenReturn(List.of(admin));

        EnrollmentRequest result = enrollmentService.requestApproval(student, 10L, EnrollmentRequestReason.FUTURE_SEMESTER);

        assertEquals(EnrollmentRequestStatus.PENDING, result.getStatus());
        assertEquals(EnrollmentRequestReason.FUTURE_SEMESTER, result.getReason());
        assertEquals(4, result.getStudentSemester());
        assertEquals(5, result.getCourseSemester());
        verify(notificationService).send(eq(admin), eq("Enrollment Approval Needed"), anyString(), eq("approval"));
    }

    @Test
    void requestApproval_createsPendingRequest_withCreditLimitReason_andSnapshotsCreditHours() {
        Course bigCourse = Course.builder().id(20L).title("Heavy Course").creditHours(16).semester(1).build();
        Enrollment existing = Enrollment.builder().id(1L).student(student).course(bigCourse).build();
        User admin = User.builder().id(99L).email("admin@studylock.com").fullName("Admin").role(UserRole.ADMIN).build();

        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(false);
        when(enrollmentRequestRepository.existsByStudentIdAndCourseIdAndStatusAndReason(
                1L, 10L, EnrollmentRequestStatus.PENDING, EnrollmentRequestReason.CREDIT_LIMIT_EXCEEDED)).thenReturn(false);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course)); // 4 credit hours
        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(List.of(existing)); // 16 already used
        when(enrollmentRequestRepository.save(any(EnrollmentRequest.class))).thenAnswer(inv -> {
            EnrollmentRequest req = inv.getArgument(0);
            req.setId(501L);
            return req;
        });
        when(userRepository.findByRole(UserRole.ADMIN)).thenReturn(List.of(admin));

        EnrollmentRequest result = enrollmentService.requestApproval(student, 10L, EnrollmentRequestReason.CREDIT_LIMIT_EXCEEDED);

        assertEquals(EnrollmentRequestStatus.PENDING, result.getStatus());
        assertEquals(EnrollmentRequestReason.CREDIT_LIMIT_EXCEEDED, result.getReason());
        assertEquals(16, result.getCurrentCreditHours());
        assertEquals(4, result.getRequestedCourseCreditHours());
        verify(notificationService).send(eq(admin), eq("Enrollment Approval Needed"), anyString(), eq("approval"));
    }

    @Test
    void requestApproval_throws_whenAlreadyHasPendingRequest_forSameReason() {
        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(false);
        when(enrollmentRequestRepository.existsByStudentIdAndCourseIdAndStatusAndReason(
                1L, 10L, EnrollmentRequestStatus.PENDING, EnrollmentRequestReason.FUTURE_SEMESTER)).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> enrollmentService.requestApproval(student, 10L, EnrollmentRequestReason.FUTURE_SEMESTER));

        assertTrue(ex.getMessage().contains("pending"));
    }

    @Test
    void approveRequest_marksApproved_andEnrollsStudent() {
        course.setSemester(5);
        EnrollmentRequest req = EnrollmentRequest.builder().id(500L).student(student).course(course)
                .status(EnrollmentRequestStatus.PENDING).reason(EnrollmentRequestReason.FUTURE_SEMESTER)
                .studentSemester(4).courseSemester(5).build();
        User admin = User.builder().id(99L).email("admin@studylock.com").fullName("Admin").role(UserRole.ADMIN).build();

        when(enrollmentRequestRepository.findById(500L)).thenReturn(Optional.of(req));
        when(enrollmentRequestRepository.save(any(EnrollmentRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(false);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(List.of());
        when(enrollmentRequestRepository.existsByStudentIdAndCourseIdAndStatusAndReason(
                1L, 10L, EnrollmentRequestStatus.APPROVED, EnrollmentRequestReason.FUTURE_SEMESTER)).thenReturn(true);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        Enrollment result = enrollmentService.approveRequest(500L, admin, "Looks fine");

        assertEquals(EnrollmentRequestStatus.APPROVED, req.getStatus());
        assertEquals(admin, req.getDecidedBy());
        assertNotNull(result);
        verify(notificationService).send(eq(student), eq("Enrollment Approved"), anyString(), eq("approval"));
    }

    @Test
    void approveRequest_withCreditLimitReason_marksApproved_andEnrollsStudentOverCap() {
        Course bigCourse = Course.builder().id(20L).title("Heavy Course").creditHours(16).semester(1).build();
        Enrollment existing = Enrollment.builder().id(1L).student(student).course(bigCourse).build();
        EnrollmentRequest req = EnrollmentRequest.builder().id(501L).student(student).course(course)
                .status(EnrollmentRequestStatus.PENDING).reason(EnrollmentRequestReason.CREDIT_LIMIT_EXCEEDED)
                .currentCreditHours(16).requestedCourseCreditHours(4).build();
        User admin = User.builder().id(99L).email("admin@studylock.com").fullName("Admin").role(UserRole.ADMIN).build();

        when(enrollmentRequestRepository.findById(501L)).thenReturn(Optional.of(req));
        when(enrollmentRequestRepository.save(any(EnrollmentRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(enrollmentRepository.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(false);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(List.of(existing)); // 16 already, +4 = 20 > 18
        when(enrollmentRequestRepository.existsByStudentIdAndCourseIdAndStatusAndReason(
                1L, 10L, EnrollmentRequestStatus.APPROVED, EnrollmentRequestReason.CREDIT_LIMIT_EXCEEDED)).thenReturn(true);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        Enrollment result = enrollmentService.approveRequest(501L, admin, "One-time exception");

        assertEquals(EnrollmentRequestStatus.APPROVED, req.getStatus());
        assertNotNull(result);
        verify(notificationService).send(eq(student), eq("Enrollment Approved"), anyString(), eq("approval"));
    }

    @Test
    void approveRequest_throws_whenRequestAlreadyDecided() {
        EnrollmentRequest req = EnrollmentRequest.builder().id(500L).student(student).course(course)
                .status(EnrollmentRequestStatus.APPROVED).build();
        User admin = User.builder().id(99L).email("admin@studylock.com").fullName("Admin").role(UserRole.ADMIN).build();

        when(enrollmentRequestRepository.findById(500L)).thenReturn(Optional.of(req));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> enrollmentService.approveRequest(500L, admin, null));

        assertTrue(ex.getMessage().contains("approved"));
    }

    @Test
    void rejectRequest_marksRejected_andNotifiesStudent() {
        EnrollmentRequest req = EnrollmentRequest.builder().id(500L).student(student).course(course)
                .status(EnrollmentRequestStatus.PENDING).build();
        User admin = User.builder().id(99L).email("admin@studylock.com").fullName("Admin").role(UserRole.ADMIN).build();

        when(enrollmentRequestRepository.findById(500L)).thenReturn(Optional.of(req));
        when(enrollmentRequestRepository.save(any(EnrollmentRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        enrollmentService.rejectRequest(500L, admin, "Not eligible yet");

        assertEquals(EnrollmentRequestStatus.REJECTED, req.getStatus());
        verify(notificationService).send(eq(student), eq("Enrollment Request Declined"), anyString(), eq("approval"));
        verify(enrollmentRepository, never()).save(any());
    }

    // ── Other existing behaviors retained ─────────────────────
    @Test
    void unenroll_deletesEnrollmentById() {
        enrollmentService.unenroll(5L);
        verify(enrollmentRepository).deleteById(5L);
    }

    @Test
    void markTopicComplete_updatesExistingProgress() {
        TopicProgress existing = TopicProgress.builder()
                .id(100L).student(student).topicName("Linked Lists")
                .completed(false).timeSpentSeconds(60).build();

        when(topicProgressRepository.findByStudentIdAndEnrollmentIdAndTopicName(1L, 10L, "Linked Lists"))
                .thenReturn(Optional.of(existing));
        when(topicProgressRepository.save(any(TopicProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        TopicProgress result = enrollmentService.markTopicComplete(student, 10L, "Linked Lists", 120);

        assertTrue(result.isCompleted());
        assertNotNull(result.getCompletedAt());
        assertEquals(180, result.getTimeSpentSeconds());
        verify(enrollmentRepository, never()).findById(any());
    }

    @Test
    void markTopicComplete_createsNewProgress_whenNoneExists() {
        Enrollment enrollment = enrollmentOf(course);

        when(topicProgressRepository.findByStudentIdAndEnrollmentIdAndTopicName(1L, 10L, "Recursion"))
                .thenReturn(Optional.empty());
        when(enrollmentRepository.findById(10L)).thenReturn(Optional.of(enrollment));
        when(topicProgressRepository.save(any(TopicProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        TopicProgress result = enrollmentService.markTopicComplete(student, 10L, "Recursion", 90);

        assertTrue(result.isCompleted());
        assertEquals("Recursion", result.getTopicName());
        assertEquals(90, result.getTimeSpentSeconds());
        assertEquals(enrollment, result.getEnrollment());
    }

    @Test
    void getStudentCourseReport_aggregatesTopicCompletionCounts() {
        Enrollment enrollment = enrollmentOf(course);
        TopicProgress done = TopicProgress.builder().id(1L).completed(true).topicName("A").build();
        TopicProgress notDone = TopicProgress.builder().id(2L).completed(false).topicName("B").build();

        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(List.of(enrollment));
        when(topicProgressRepository.findByEnrollmentId(10L)).thenReturn(List.of(done, notDone));

        var report = enrollmentService.getStudentCourseReport(1L);

        assertEquals(1, report.get("totalCourses"));
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> details = (List<java.util.Map<String, Object>>) report.get("details");
        assertEquals(1, details.size());
        assertEquals(1L, details.get(0).get("completedTopics"));
        assertEquals(2, details.get(0).get("totalTopics"));
    }

    @Test
    void getCurrentCreditHours_sumsCreditHoursAcrossEnrollments() {
        Course c1 = Course.builder().id(1L).creditHours(3).build();
        Course c2 = Course.builder().id(2L).creditHours(4).build();
        when(enrollmentRepository.findByStudentIdWithCourse(1L)).thenReturn(List.of(
                Enrollment.builder().id(1L).student(student).course(c1).build(),
                Enrollment.builder().id(2L).student(student).course(c2).build()
        ));

        assertEquals(7, enrollmentService.getCurrentCreditHours(1L));
    }
}
