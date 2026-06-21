package com.studylock.controller;

import com.studylock.model.*;
import com.studylock.service.*;
import com.studylock.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final SecurityUtils securityUtils;
    private final NotificationService notificationService;

    @GetMapping("/setup")
    public String setup(Model model) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        model.addAttribute("activeSession", sessionService.getActiveSession(user.getId()).orElse(null));
        model.addAttribute("gameState",
            sessionService.getGameState(user.getId()).orElse(null));
        model.addAttribute("stats", sessionService.getStudyStats(user.getId()));
        return "session/setup";
    }

    // ✅ FIX: Form POST (not JSON) — accept @RequestParam
    @PostMapping("/start")
    public String start(@RequestParam(defaultValue = "60") int plannedMinutes,
                        @RequestParam(defaultValue = "false") boolean cameraEnabled,
                        Model model) {
        User user = securityUtils.getCurrentUser();
        // End any existing active session first
        sessionService.getActiveSession(user.getId()).ifPresent(s ->
            sessionService.endSession(s.getId(), SessionStatus.FAILED, s.getElapsedSeconds()));
        sessionService.startSession(user, plannedMinutes, cameraEnabled, List.of());
        return "redirect:/session/active";
    }

    @GetMapping("/active")
    public String active(Model model) {
        User user = securityUtils.getCurrentUser();
        StudySession session = sessionService.getActiveSession(user.getId()).orElse(null);
        if (session == null) return "redirect:/session/setup";
        model.addAttribute("user", user);
        model.addAttribute("session", session);
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        return "session/active";
    }

    // ── REST endpoints for JS calls from session/active.html ──

    @PostMapping("/api/violation")
    @ResponseBody
    public ResponseEntity<?> recordViolation() {
        User user = securityUtils.getCurrentUser();
        StudySession session = sessionService.getActiveSession(user.getId()).orElse(null);
        if (session == null)
            return ResponseEntity.badRequest().body(Map.of("error", "No active session"));
        session = sessionService.recordViolation(session.getId());
        return ResponseEntity.ok(Map.of("violations", session.getViolations(), "maxViolations", 3));
    }

    @PostMapping("/api/elapsed")
    @ResponseBody
    public ResponseEntity<?> updateElapsed(@RequestBody Map<String, Object> body) {
        User user = securityUtils.getCurrentUser();
        sessionService.getActiveSession(user.getId()).ifPresent(s ->
            sessionService.updateElapsed(s.getId(),
                Integer.parseInt(body.getOrDefault("elapsed", "0").toString())));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/api/end")
    @ResponseBody
    public ResponseEntity<?> endSession(@RequestBody Map<String, Object> body) {
        User user = securityUtils.getCurrentUser();
        StudySession session = sessionService.getActiveSession(user.getId()).orElse(null);
        if (session == null)
            return ResponseEntity.badRequest().body(Map.of("error", "No active session", "success", false));
        String statusStr = (String) body.getOrDefault("status", "completed");
        int elapsed = Integer.parseInt(body.getOrDefault("elapsed", "0").toString());
        SessionStatus ss = "completed".equalsIgnoreCase(statusStr)
            ? SessionStatus.COMPLETED : SessionStatus.FAILED;
        StudySession ended = sessionService.endSession(session.getId(), ss, elapsed);
        return ResponseEntity.ok(Map.of(
            "success",      true,
            "xpEarned",     ended.getXpEarned(),
            "violations",   ended.getViolations(),
            "validSession", ended.isValidSession()
        ));
    }

    @GetMapping("/result")
    public String result(Model model,
                         @RequestParam(defaultValue = "completed") String status,
                         @RequestParam(defaultValue = "0") int duration,
                         @RequestParam(defaultValue = "0") int violations,
                         @RequestParam(defaultValue = "0") int xp) {
        User user = securityUtils.getCurrentUser();
        model.addAttribute("user", user);
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getId()));
        model.addAttribute("status", status);
        model.addAttribute("duration", duration);
        model.addAttribute("violations", violations);
        model.addAttribute("xp", xp);
        return "session/result";
    }
}
