package com.studylock.service;

import com.studylock.model.*;
import com.studylock.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final StudySessionRepository sessionRepository;
    private final GameStateRepository gameStateRepository;
    private final NotificationService notificationService;

    @Transactional
    public StudySession startSession(User user, int plannedMinutes,
                                     boolean cameraEnabled, List<String> blockedApps) {
        sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.ACTIVE)
            .ifPresent(s -> {
                s.setStatus(SessionStatus.FAILED);
                s.setEndedAt(LocalDateTime.now());
                sessionRepository.save(s);
            });
        String appsJson = blockedApps != null ? "[\"" + String.join("\",\"", blockedApps) + "\"]" : "[]";
        StudySession session = StudySession.builder()
            .user(user)
            .plannedDurationSeconds(plannedMinutes * 60)
            .cameraEnabled(cameraEnabled)
            .blockedApps(appsJson)
            .status(SessionStatus.ACTIVE)
            .build();
        return sessionRepository.save(session);
    }

    @Transactional
    public StudySession recordViolation(Long sessionId) {
        StudySession session = sessionRepository.findById(sessionId).orElseThrow();
        session.setViolations(session.getViolations() + 1);
        return sessionRepository.save(session);
    }

    @Transactional
    public StudySession updateElapsed(Long sessionId, int elapsedSeconds) {
        StudySession session = sessionRepository.findById(sessionId).orElseThrow();
        session.setElapsedSeconds(elapsedSeconds);
        return sessionRepository.save(session);
    }

    @Transactional
    public StudySession endSession(Long sessionId, SessionStatus status, int elapsedSeconds) {
        StudySession session = sessionRepository.findById(sessionId).orElseThrow();
        boolean isValid = status == SessionStatus.COMPLETED && session.getViolations() < 3;
        session.setStatus(status);
        session.setElapsedSeconds(elapsedSeconds);
        session.setValidSession(isValid);
        session.setEndedAt(LocalDateTime.now());
        session = sessionRepository.save(session);
        if (isValid) awardXp(session);
        return session;
    }

    private void awardXp(StudySession session) {
        try {
            int xp = session.getXpEarned();
            Optional<GameState> gsOpt = gameStateRepository.findByUserId(session.getUser().getId());
            if (gsOpt.isEmpty()) return;
            GameState gs = gsOpt.get();
            gs.setPoints(gs.getPoints() + xp);
            LocalDate today = LocalDate.now();
            if (gs.getLastSessionDate() == null || !gs.getLastSessionDate().equals(today)) {
                int diff = gs.getLastSessionDate() == null ? 999 :
                    (int) ChronoUnit.DAYS.between(gs.getLastSessionDate(), today);
                int newStreak = diff <= 1 ? gs.getStreak() + 1 : 1;
                gs.setStreak(newStreak);
                if (newStreak > gs.getLongestStreak()) gs.setLongestStreak(newStreak);
                gs.setLastSessionDate(today);
            }
            gameStateRepository.save(gs);
            notificationService.send(session.getUser(), "Session Complete!",
                "You earned " + xp + " XP! Keep up the great work.", "badge");
        } catch (Exception ignored) {}
    }

    public List<StudySession> getUserSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByStartedAtDesc(userId);
    }

    public Optional<StudySession> getActiveSession(Long userId) {
        return sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE);
    }

    public Map<String, Object> getStudyStats(Long userId) {
        List<StudySession> sessions = sessionRepository.findByUserIdAndValidSessionTrue(userId);
        int totalSeconds = sessions.stream().mapToInt(StudySession::getElapsedSeconds).sum();
        LocalDate today = LocalDate.now();
        int todaySeconds = sessions.stream()
            .filter(s -> s.getStartedAt().toLocalDate().equals(today))
            .mapToInt(StudySession::getElapsedSeconds).sum();
        return Map.of(
            "totalSeconds", totalSeconds,
            "todaySeconds", todaySeconds,
            "sessionCount", sessions.size()
        );
    }

    public long getActiveSessions() {
        return sessionRepository.findAll().stream()
            .filter(s -> s.getStatus() == SessionStatus.ACTIVE).count();
    }

    public Map<String, Object> getGlobalStats() {
        List<StudySession> all = sessionRepository.findAll();
        long completed = all.stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).count();
        long failed = all.stream().filter(s -> s.getStatus() == SessionStatus.FAILED).count();
        int totalSeconds = all.stream().filter(StudySession::isValidSession)
            .mapToInt(StudySession::getElapsedSeconds).sum();
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", all.size());
        stats.put("completed", completed);
        stats.put("failed", failed);
        stats.put("totalHours", totalSeconds / 3600);
        stats.put("totalMinutes", totalSeconds / 60);
        return stats;
    }

    public Optional<GameState> getGameState(Long userId) {
        return gameStateRepository.findByUserId(userId);
    }
}
