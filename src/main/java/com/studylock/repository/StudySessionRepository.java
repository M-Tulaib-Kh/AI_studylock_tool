package com.studylock.repository;

import com.studylock.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface StudySessionRepository extends JpaRepository<StudySession, Long> {
    List<StudySession> findByUserIdOrderByStartedAtDesc(Long userId);
    Optional<StudySession> findByUserIdAndStatus(Long userId, SessionStatus status);
    List<StudySession> findByUserIdAndValidSessionTrue(Long userId);
}
