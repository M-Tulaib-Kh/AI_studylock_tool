package com.studylock.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "login_events")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LoginEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    private String email;
    private String name;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "logged_in_at")
    private LocalDateTime loggedInAt;

    @PrePersist
    protected void onCreate() { loggedInAt = LocalDateTime.now(); }
}
