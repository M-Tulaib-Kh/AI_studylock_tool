package com.studylock.controller;

import com.studylock.model.User;
import com.studylock.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final SecurityUtils securityUtils;

    @GetMapping("/")
    public String home() {
        User user = securityUtils.getCurrentUser();
        if (user == null) return "redirect:/auth/login";
        return switch (user.getRole()) {
            case ADMIN -> "redirect:/admin/dashboard";
            case LECTURER -> "redirect:/lecturer/home";
            default -> "redirect:/student/home";
        };
    }
}
