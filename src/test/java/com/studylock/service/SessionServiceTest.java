package com.studylock.service;

import com.studylock.model.SessionStatus;
import com.studylock.model.StudySession;
import com.studylock.repository.GameStateRepository;
import com.studylock.repository.StudySessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock private StudySessionRepository sessionRepository;
    @Mock private GameStateRepository gameStateRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private SessionService sessionService;

    @Test
    void getGlobalStats_includesBothTotalHoursAndTotalMinutes() {
        // Regression test for the admin Reports tab bug: the template reads
        // sessionStats.totalMinutes, but the service previously only returned totalHours.
        StudySession s1 = StudySession.builder().id(1L).status(SessionStatus.COMPLETED)
                .validSession(true).elapsedSeconds(3600).build(); // 1 hour
        StudySession s2 = StudySession.builder().id(2L).status(SessionStatus.FAILED)
                .validSession(false).elapsedSeconds(600).build(); // not counted (invalid)
        StudySession s3 = StudySession.builder().id(3L).status(SessionStatus.COMPLETED)
                .validSession(true).elapsedSeconds(1800).build(); // 30 min

        when(sessionRepository.findAll()).thenReturn(List.of(s1, s2, s3));

        Map<String, Object> stats = sessionService.getGlobalStats();

        assertEquals(3, stats.get("total"));
        assertEquals(2L, stats.get("completed"));
        assertEquals(1L, stats.get("failed"));
        // (3600 + 1800) seconds = 5400s = 1 hour (int division), 90 minutes
        assertEquals(1, stats.get("totalHours"));
        assertEquals(90, stats.get("totalMinutes"));
    }

    @Test
    void getGlobalStats_returnsZeroes_whenNoSessionsExist() {
        when(sessionRepository.findAll()).thenReturn(List.of());

        Map<String, Object> stats = sessionService.getGlobalStats();

        assertEquals(0, stats.get("total"));
        assertEquals(0L, stats.get("completed"));
        assertEquals(0L, stats.get("failed"));
        assertEquals(0, stats.get("totalHours"));
        assertEquals(0, stats.get("totalMinutes"));
    }
}
