package com.studylock.util;

import com.studylock.model.User;
import com.studylock.model.UserRole;
import com.studylock.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityUtilsTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SecurityUtils securityUtils;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_returnsNull_whenNoAuthentication() {
        SecurityContextHolder.clearContext();
        assertNull(securityUtils.getCurrentUser());
    }

    @Test
    void getCurrentUser_returnsUser_whenAuthenticated() {
        User user = User.builder().id(5L).email("student@studylock.com")
                .fullName("Test Student").role(UserRole.STUDENT).active(true).build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student@studylock.com", "password", java.util.List.of()));
        when(userRepository.findByEmail("student@studylock.com")).thenReturn(Optional.of(user));

        User result = securityUtils.getCurrentUser();

        assertNotNull(result);
        assertEquals(5L, result.getId());
        assertEquals(UserRole.STUDENT, result.getRole());
    }

    @Test
    void getCurrentUserId_returnsNull_whenUserNotFound() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ghost@studylock.com", "password", java.util.List.of()));
        when(userRepository.findByEmail("ghost@studylock.com")).thenReturn(Optional.empty());

        assertNull(securityUtils.getCurrentUserId());
    }

    @Test
    void getCurrentUserId_returnsId_whenUserFound() {
        User user = User.builder().id(7L).email("lect@studylock.com")
                .fullName("Lect").role(UserRole.LECTURER).active(true).build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("lect@studylock.com", "password", java.util.List.of()));
        when(userRepository.findByEmail("lect@studylock.com")).thenReturn(Optional.of(user));

        assertEquals(7L, securityUtils.getCurrentUserId());
    }
}
