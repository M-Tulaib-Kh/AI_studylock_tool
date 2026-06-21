package com.studylock.repository;

import com.studylock.model.LoginEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LoginEventRepository extends JpaRepository<LoginEvent, Long> {
    List<LoginEvent> findAllByOrderByLoggedInAtDesc();
    List<LoginEvent> findTop20ByOrderByLoggedInAtDesc();
    List<LoginEvent> findTop50ByOrderByLoggedInAtDesc();
    List<LoginEvent> findByUserIdOrderByLoggedInAtDesc(Long userId);
}
