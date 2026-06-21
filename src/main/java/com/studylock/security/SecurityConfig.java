package com.studylock.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomLoginSuccessHandler successHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authProvider() {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider();
        p.setUserDetailsService(userDetailsService);
        p.setPasswordEncoder(passwordEncoder());
        return p;
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authProvider())
            .authorizeHttpRequests(auth -> auth
                // ── Public static resources ──────────────────────────────
                // FIX: Spring Boot 3 / PathPatternParser does NOT allow characters
                // after ** in a pattern. Use AntPathRequestMatcher for static files.
                .requestMatchers(
                    new AntPathRequestMatcher("/css/**"),
                    new AntPathRequestMatcher("/js/**"),
                    new AntPathRequestMatcher("/images/**"),
                    new AntPathRequestMatcher("/webjars/**"),
                    new AntPathRequestMatcher("/uploads/**")
                ).permitAll()
                // ── Simple exact/prefix patterns — safe for PathPatternParser ──
                .requestMatchers("/auth/login", "/auth/logout", "/auth/register",
                                 "/error", "/favicon.ico").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/lecturer/**").hasRole("LECTURER")
                .requestMatchers("/student/**").hasRole("STUDENT")
                .requestMatchers("/session/**").hasRole("STUDENT")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/login")
                .successHandler(successHandler)
                .failureUrl("/auth/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .logoutSuccessUrl("/auth/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .clearAuthentication(true)
                .permitAll()
            )
            .csrf(csrf -> csrf
                // FIX: Use AntPathRequestMatcher for all CSRF exclusions.
                // Spring Boot 3 PathPatternParser rejects **/submit-file style patterns
                // (nothing allowed after **). AntPathRequestMatcher uses Ant-style
                // matching which correctly handles ** anywhere.
                .ignoringRequestMatchers(
                    new AntPathRequestMatcher("/session/api/**"),
                    new AntPathRequestMatcher("/admin/api/**"),
                    new AntPathRequestMatcher("/lecturer/ai/**"),
                    new AntPathRequestMatcher("/lecturer/ai-tutor/**"),
                    new AntPathRequestMatcher("/student/lecture/**"),
                    new AntPathRequestMatcher("/student/quiz/**"),
                    new AntPathRequestMatcher("/student/ai-tutor/**"),
                    new AntPathRequestMatcher("/student/topic-session/**")
                )
            )
            .sessionManagement(sm -> sm
                .maximumSessions(5)
                .sessionRegistry(sessionRegistry())
                .expiredUrl("/auth/login?expired=true")
            );

        return http.build();
    }
}
