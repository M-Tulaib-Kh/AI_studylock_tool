package com.studylock.controller;

import com.studylock.dto.RegisterDto;
import com.studylock.service.UserService;
import com.studylock.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final SecurityUtils securityUtils;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                             @RequestParam(required = false) String logout,
                             Model model) {
        if (securityUtils.getCurrentUser() != null) return "redirect:/";
        if (error != null) model.addAttribute("error", "Invalid email or password");
        if (logout != null) model.addAttribute("success", "You have been logged out");
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (securityUtils.getCurrentUser() != null) return "redirect:/";
        model.addAttribute("registerDto", new RegisterDto());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterDto dto,
                            BindingResult result, Model model,
                            RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            model.addAttribute("registerDto", dto);
            return "auth/register";
        }
        try {
            userService.register(dto);
            redirectAttrs.addFlashAttribute("success", "Account created! Please login.");
            return "redirect:/auth/login";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("registerDto", dto);
            return "auth/register";
        }
    }
}
