package com.studylock.service;

import com.studylock.model.Notification;
import com.studylock.model.User;
import com.studylock.repository.NotificationRepository;
import com.studylock.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public void send(User user, String title, String message, String type) {
        Notification n = new Notification();
        n.setUser(user); n.setTitle(title); n.setMessage(message); n.setType(type);
        notificationRepository.save(n);
    }

    public void create(Long userId, String title, String message, String type) {
        User user = userRepository.findById(userId).orElseThrow();
        send(user, title, message, type);
    }

    public List<Notification> getUserNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public void markAllRead(Long userId) {
        List<Notification> list = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        list.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(list);
    }

    public void markRead(Long notifId) {
        notificationRepository.findById(notifId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }
}
