package com.studylock.service;

import com.studylock.dto.RegisterDto;
import com.studylock.model.*;
import com.studylock.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final GameStateRepository gameStateRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegisterDto dto) {
        if (userRepository.existsByEmail(dto.getEmail().toLowerCase()))
            throw new RuntimeException("Email already registered");
        User user = User.builder()
            .email(dto.getEmail().toLowerCase().trim())
            .password(passwordEncoder.encode(dto.getPassword()))
            .fullName(dto.getFullName().trim())
            .role(dto.getRole() != null ? dto.getRole() : UserRole.STUDENT)
            .department(dto.getDepartment())
            .registrationNo(dto.getRegistrationNo())
            .designation(dto.getDesignation())
            .semester((dto.getRole() == UserRole.STUDENT) ? (dto.getSemester() != null ? dto.getSemester() : 1) : null)
            .active(true).build();
        user = userRepository.save(user);
        initGameState(user);
        return user;
    }

    @Transactional
    public User adminCreateUser(String fullName, String email, String password, UserRole role) {
        return adminCreateUser(fullName, email, password, role, null);
    }

    @Transactional
    public User adminCreateUser(String fullName, String email, String password, UserRole role, Integer semester) {
        if (userRepository.existsByEmail(email.toLowerCase()))
            throw new RuntimeException("Email already registered");
        User user = User.builder()
            .email(email.toLowerCase().trim())
            .password(passwordEncoder.encode(password))
            .fullName(fullName.trim()).role(role)
            .semester(role == UserRole.STUDENT ? (semester != null ? semester : 1) : null)
            .active(true).build();
        user = userRepository.save(user);
        initGameState(user);
        return user;
    }

    private void initGameState(User user) {
        String badges = "[{\"id\":\"first_session\",\"name\":\"First Step\",\"description\":\"Complete your first study session\",\"icon\":\"award\",\"unlocked\":false},"
            + "{\"id\":\"streak_3\",\"name\":\"On a Roll\",\"description\":\"Maintain a 3-day streak\",\"icon\":\"flame\",\"unlocked\":false},"
            + "{\"id\":\"no_violations\",\"name\":\"Laser Focus\",\"description\":\"Zero violations in a session\",\"icon\":\"eye\",\"unlocked\":false}]";
        String challenges = "[{\"id\":\"daily_1h\",\"title\":\"Daily Focus\",\"description\":\"Study 1 hour today\",\"target\":3600,\"current\":0,\"reward\":50,\"completed\":false}]";
        GameState gs = GameState.builder()
            .user(user).points(0).streak(0).longestStreak(0)
            .badgesJson(badges).challengesJson(challenges).build();
        gameStateRepository.save(gs);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase())
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public List<User> findAll() { return userRepository.findAll(); }
    public List<User> findByRole(UserRole role) { return userRepository.findByRole(role); }

    @Transactional
    public User updateProfile(Long id, String fullName, String department, String regNo, String designation) {
        User u = findById(id);
        if (fullName != null && !fullName.isBlank()) u.setFullName(fullName.trim());
        if (department != null) u.setDepartment(department);
        if (regNo != null) u.setRegistrationNo(regNo);
        if (designation != null) u.setDesignation(designation);
        return userRepository.save(u);
    }

    @Transactional
    public void updateAvatar(Long id, String avatarUrl) {
        User u = findById(id); u.setAvatarUrl(avatarUrl); userRepository.save(u);
    }

    @Transactional
    public void changeRole(Long id, UserRole role) {
        User u = findById(id); u.setRole(role); userRepository.save(u);
    }

    @Transactional
    public void toggleActive(Long id) {
        User u = findById(id); u.setActive(!u.isActive()); userRepository.save(u);
    }

    @Transactional
    public void updateSemester(Long id, Integer semester) {
        User u = findById(id);
        if (u.getRole() != UserRole.STUDENT) throw new RuntimeException("Only students have a semester");
        u.setSemester(semester);
        userRepository.save(u);
    }

    @Transactional
    public void deleteUser(Long id) { userRepository.deleteById(id); }

    @Transactional
    public void changePassword(Long id, String oldPwd, String newPwd) {
        User u = findById(id);
        if (!passwordEncoder.matches(oldPwd, u.getPassword()))
            throw new RuntimeException("Current password is incorrect");
        u.setPassword(passwordEncoder.encode(newPwd));
        userRepository.save(u);
    }

    public Map<String, Long> getStats() {
        Map<String, Long> s = new HashMap<>();
        s.put("total", userRepository.count());
        s.put("students", userRepository.countByRole(UserRole.STUDENT));
        s.put("lecturers", userRepository.countByRole(UserRole.LECTURER));
        s.put("admins", userRepository.countByRole(UserRole.ADMIN));
        return s;
    }

    public List<User> getTopStudents() {
        return userRepository.findByRole(UserRole.STUDENT).stream().limit(10).toList();
    }
}
