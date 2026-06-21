package com.studylock.repository;

import com.studylock.model.TopicProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TopicProgressRepository extends JpaRepository<TopicProgress, Long> {
    List<TopicProgress> findByEnrollmentId(Long enrollmentId);
    List<TopicProgress> findByStudentId(Long studentId);
    Optional<TopicProgress> findByStudentIdAndEnrollmentIdAndTopicName(Long studentId, Long enrollmentId, String topicName);
}
