package com.studylock.service;

import com.studylock.model.*;
import com.studylock.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private LectureRepository lectureRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private QuizRepository quizRepository;
    @Mock private QuizAttemptRepository quizAttemptRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private AcademicService academicService;

    private User lecturer;
    private Course course;

    @BeforeEach
    void setUp() {
        lecturer = User.builder().id(2L).email("lecturer@studylock.com")
                .fullName("Test Lecturer").role(UserRole.LECTURER).active(true).build();
        course = Course.builder().id(10L).title("Web Development").subject("Web Dev")
                .lecturer(lecturer).build();
    }

    @Test
    void assignLecturer_updatesCourseLecturer() {
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        User newLecturer = User.builder().id(3L).email("new@studylock.com")
                .fullName("New Lecturer").role(UserRole.LECTURER).build();

        Course result = academicService.assignLecturer(10L, newLecturer);

        assertEquals(newLecturer, result.getLecturer());
        verify(courseRepository).save(course);
    }

    @Test
    void assignLecturer_throws_whenCourseNotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> academicService.assignLecturer(99L, lecturer));

        assertEquals("Course not found", ex.getMessage());
    }

    @Test
    void adminCreateCourse_savesCourseWithTrimmedTitle() {
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> {
            Course c = inv.getArgument(0);
            c.setId(20L);
            return c;
        });

        Course result = academicService.adminCreateCourse("  Cybersecurity  ", "desc", "Security", lecturer);

        assertEquals("Cybersecurity", result.getTitle());
        assertEquals(lecturer, result.getLecturer());
        assertEquals(3, result.getCreditHours());
        assertEquals(1, result.getSemester());
    }

    @Test
    void adminCreateCourse_withCreditHoursAndSemester_setsBothFields() {
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> {
            Course c = inv.getArgument(0);
            c.setId(21L);
            return c;
        });

        Course result = academicService.adminCreateCourse("AI Capstone", "desc", "AI", lecturer, 4, 7);

        assertEquals(4, result.getCreditHours());
        assertEquals(7, result.getSemester());
    }

    @Test
    void submitAssignment_marksLate_whenAssignmentOverdue() {
        Assignment assignment = Assignment.builder().id(30L).title("HW1")
                .deadline(LocalDateTime.now().minusDays(1)).totalMarks(100).build();
        User student = User.builder().id(1L).email("s@b.com").fullName("Student").role(UserRole.STUDENT).build();

        when(assignmentRepository.findById(30L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndStudentId(30L, 1L)).thenReturn(Optional.empty());
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission result = academicService.submitAssignment(30L, student, "/uploads/x.pdf", "x.pdf");

        assertEquals(SubmissionStatus.LATE, result.getStatus());
    }

    @Test
    void submitAssignment_marksSubmitted_whenBeforeDeadline() {
        Assignment assignment = Assignment.builder().id(31L).title("HW2")
                .deadline(LocalDateTime.now().plusDays(1)).totalMarks(100).build();
        User student = User.builder().id(1L).email("s@b.com").fullName("Student").role(UserRole.STUDENT).build();

        when(assignmentRepository.findById(31L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndStudentId(31L, 1L)).thenReturn(Optional.empty());
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission result = academicService.submitAssignment(31L, student, "/uploads/y.pdf", "y.pdf");

        assertEquals(SubmissionStatus.SUBMITTED, result.getStatus());
    }

    @Test
    void gradeSubmission_setsMarksAndNotifiesStudent() {
        User student = User.builder().id(1L).email("s@b.com").fullName("Student").role(UserRole.STUDENT).build();
        Assignment assignment = Assignment.builder().id(30L).title("HW1").totalMarks(100).build();
        Submission submission = Submission.builder().id(40L).student(student).assignment(assignment)
                .status(SubmissionStatus.SUBMITTED).build();

        when(submissionRepository.findById(40L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        Submission result = academicService.gradeSubmission(40L, 85, "Great work", lecturer);

        assertEquals(85, result.getMarksObtained());
        assertEquals("Great work", result.getFeedback());
        assertEquals(SubmissionStatus.GRADED, result.getStatus());
        verify(notificationService).send(eq(student), eq("Assignment Graded"), anyString(), eq("grade"));
    }

    @Test
    void getLecturerCourses_delegatesToRepository() {
        when(courseRepository.findByLecturerId(2L)).thenReturn(List.of(course));

        List<Course> result = academicService.getLecturerCourses(2L);

        assertEquals(1, result.size());
        assertEquals("Web Development", result.get(0).getTitle());
    }

    @Test
    void deleteCourse_delegatesToRepository() {
        academicService.deleteCourse(10L);
        verify(courseRepository).deleteById(10L);
    }

    @Test
    void getStudentAttempts_usesFetchJoinedQuery_toAvoidLazyInitializationException() {
        // Regression test: admin/student_report.html (and student/home, student/academic)
        // read attempt.quiz.title outside the Hibernate session. getStudentAttempts() MUST
        // use the JOIN FETCH variant so the Quiz association is already initialized by the
        // time the view renders, instead of the plain findByStudentIdOrderByCreatedAtDesc
        // which leaves quiz as an uninitialized lazy proxy.
        com.studylock.model.Quiz quiz = com.studylock.model.Quiz.builder().id(1L).title("DSA Quiz 1").build();
        com.studylock.model.QuizAttempt attempt = com.studylock.model.QuizAttempt.builder()
            .id(100L).quiz(quiz).score(8).totalMarks(10).build();

        when(quizAttemptRepository.findByStudentIdWithQuizOrderByCreatedAtDesc(1L))
            .thenReturn(List.of(attempt));

        List<com.studylock.model.QuizAttempt> result = academicService.getStudentAttempts(1L);

        assertEquals(1, result.size());
        assertEquals("DSA Quiz 1", result.get(0).getQuiz().getTitle());
        verify(quizAttemptRepository).findByStudentIdWithQuizOrderByCreatedAtDesc(1L);
        verify(quizAttemptRepository, never()).findByStudentIdOrderByCreatedAtDesc(any());
    }
}
