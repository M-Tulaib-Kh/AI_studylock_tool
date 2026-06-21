package com.studylock.service;

import com.studylock.dto.RegisterDto;
import com.studylock.model.*;
import com.studylock.repository.GameStateRepository;
import com.studylock.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameStateRepository gameStateRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void register_createsUserWithEncodedPassword_andInitGameState() {
        RegisterDto dto = new RegisterDto();
        dto.setEmail("New.Student@Example.com");
        dto.setPassword("plainPass");
        dto.setFullName("New Student");
        dto.setRole(UserRole.STUDENT);

        when(userRepository.existsByEmail("new.student@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainPass")).thenReturn("ENCODED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        User result = userService.register(dto);

        assertEquals("new.student@example.com", result.getEmail());
        assertEquals("ENCODED", result.getPassword());
        assertEquals(UserRole.STUDENT, result.getRole());
        assertTrue(result.isActive());
        assertEquals(1, result.getSemester());
        verify(gameStateRepository).save(any(GameState.class));
    }

    @Test
    void register_leavesSemesterNull_forLecturer() {
        RegisterDto dto = new RegisterDto();
        dto.setEmail("lecturer@example.com");
        dto.setPassword("plainPass");
        dto.setFullName("New Lecturer");
        dto.setRole(UserRole.LECTURER);

        when(userRepository.existsByEmail("lecturer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainPass")).thenReturn("ENCODED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.register(dto);

        assertNull(result.getSemester());
    }

    @Test
    void register_respectsExplicitSemester() {
        RegisterDto dto = new RegisterDto();
        dto.setEmail("senior@example.com");
        dto.setPassword("plainPass");
        dto.setFullName("Senior Student");
        dto.setRole(UserRole.STUDENT);
        dto.setSemester(6);

        when(userRepository.existsByEmail("senior@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainPass")).thenReturn("ENCODED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.register(dto);

        assertEquals(6, result.getSemester());
    }

    @Test
    void register_throws_whenEmailAlreadyExists() {
        RegisterDto dto = new RegisterDto();
        dto.setEmail("existing@studylock.com");
        dto.setPassword("pass");
        dto.setFullName("Existing User");

        when(userRepository.existsByEmail("existing@studylock.com")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.register(dto));
        assertEquals("Email already registered", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_throws_whenOldPasswordIncorrect() {
        User user = User.builder().id(1L).email("a@b.com").password("OLD_ENCODED")
                .fullName("A").role(UserRole.STUDENT).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongOld", "OLD_ENCODED")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.changePassword(1L, "wrongOld", "newPass"));

        assertEquals("Current password is incorrect", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_succeeds_whenOldPasswordCorrect() {
        User user = User.builder().id(1L).email("a@b.com").password("OLD_ENCODED")
                .fullName("A").role(UserRole.STUDENT).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctOld", "OLD_ENCODED")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("NEW_ENCODED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.changePassword(1L, "correctOld", "newPass");

        assertEquals("NEW_ENCODED", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void toggleActive_flipsActiveFlag() {
        User user = User.builder().id(1L).email("a@b.com").password("x")
                .fullName("A").role(UserRole.STUDENT).active(true).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.toggleActive(1L);

        assertFalse(user.isActive());
    }

    @Test
    void getStats_returnsCountsByRole() {
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countByRole(UserRole.STUDENT)).thenReturn(7L);
        when(userRepository.countByRole(UserRole.LECTURER)).thenReturn(2L);
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        var stats = userService.getStats();

        assertEquals(10L, stats.get("total"));
        assertEquals(7L, stats.get("students"));
        assertEquals(2L, stats.get("lecturers"));
        assertEquals(1L, stats.get("admins"));
    }

    @Test
    void findById_throws_whenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.findById(99L));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void findByRole_delegatesToRepository() {
        List<User> lecturers = List.of(User.builder().id(2L).email("l@b.com").password("x")
                .fullName("Lecturer").role(UserRole.LECTURER).build());
        when(userRepository.findByRole(UserRole.LECTURER)).thenReturn(lecturers);

        List<User> result = userService.findByRole(UserRole.LECTURER);

        assertEquals(1, result.size());
        assertEquals(UserRole.LECTURER, result.get(0).getRole());
    }

    @Test
    void updateSemester_updatesStudentSemester() {
        User student = User.builder().id(1L).email("s@b.com").password("x")
                .fullName("S").role(UserRole.STUDENT).semester(2).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateSemester(1L, 5);

        assertEquals(5, student.getSemester());
        verify(userRepository).save(student);
    }

    @Test
    void updateSemester_throws_whenUserIsNotStudent() {
        User lecturer = User.builder().id(2L).email("l@b.com").password("x")
                .fullName("L").role(UserRole.LECTURER).build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(lecturer));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.updateSemester(2L, 3));

        assertEquals("Only students have a semester", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void adminCreateUser_setsDefaultSemester_whenStudentAndNoneProvided() {
        when(userRepository.existsByEmail("admin.created@studylock.com")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("ENCODED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.adminCreateUser("Admin Created", "admin.created@studylock.com", "pass123", UserRole.STUDENT, null);

        assertEquals(1, result.getSemester());
    }

    @Test
    void adminCreateUser_setsNullSemester_forAdminRole() {
        when(userRepository.existsByEmail("super.admin@studylock.com")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("ENCODED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.adminCreateUser("Super Admin", "super.admin@studylock.com", "pass123", UserRole.ADMIN, 3);

        assertNull(result.getSemester());
    }
}
