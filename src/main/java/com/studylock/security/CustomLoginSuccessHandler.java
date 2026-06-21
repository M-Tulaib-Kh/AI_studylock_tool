package com.studylock.security;

import com.studylock.model.User;
import com.studylock.repository.UserRepository;
import com.studylock.service.LoginEventService;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final LoginEventService loginEventService;

    @Override
public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
                                     Authentication auth) throws IOException {

    Optional<User> userOpt = userRepository.findByEmail(auth.getName());

    userOpt.ifPresent(user ->
            loginEventService.record(
                    user,
                    req.getHeader("User-Agent") + " | IP: " + req.getRemoteAddr()
            )
    );

    String redirect = "/student/home";

    if (auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
        redirect = "/admin/dashboard";
    } else if (auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_LECTURER"))) {
        redirect = "/lecturer/home";
    }

    res.sendRedirect(redirect);
}
}
