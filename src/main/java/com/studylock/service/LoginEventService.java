package com.studylock.service;

import com.studylock.model.LoginEvent;
import com.studylock.model.User;
import com.studylock.repository.LoginEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoginEventService {

    private final LoginEventRepository loginEventRepository;

    public void record(User user, String deviceInfo) {
        LoginEvent e = new LoginEvent();
        e.setUser(user); e.setRole(user.getRole());
        e.setEmail(user.getEmail()); e.setName(user.getFullName());
        e.setDeviceInfo(deviceInfo);
        loginEventRepository.save(e);
    }

    public List<LoginEvent> getRecent() {
        return loginEventRepository.findTop20ByOrderByLoggedInAtDesc();
    }

    public List<LoginEvent> getAll() {
        return loginEventRepository.findAllByOrderByLoggedInAtDesc();
    }

    public List<Map<String, Object>> getDailyStats() {
        List<LoginEvent> all = loginEventRepository.findAll();
        Map<String, Long> daily = all.stream().collect(Collectors.groupingBy(
            e -> e.getLoggedInAt().toLocalDate().format(DateTimeFormatter.ofPattern("MMM dd")),
            LinkedHashMap::new, Collectors.counting()));
        List<Map<String, Object>> result = new ArrayList<>();
        daily.entrySet().stream().skip(Math.max(0, daily.size() - 14)).forEach(entry -> {
            Map<String, Object> m = new HashMap<>();
            m.put("date", entry.getKey()); m.put("count", entry.getValue());
            result.add(m);
        });
        return result;
    }
}
