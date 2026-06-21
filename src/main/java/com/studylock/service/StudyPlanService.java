package com.studylock.service;

import com.studylock.model.*;
import com.studylock.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StudyPlanService {

    private final StudyPlanRepository studyPlanRepository;
    private final OpenRouterService openRouterService;

    @Transactional
    public StudyPlan generateAndSave(User student, String subject, String goals, int weeks) {
        String planJson = openRouterService.generateStudyPlan(subject, goals, weeks);
        StudyPlan plan = StudyPlan.builder()
            .student(student)
            .title(subject + " — " + weeks + "-Week Study Plan")
            .subject(subject)
            .durationWeeks(weeks)
            .planJson(planJson)
            .build();
        return studyPlanRepository.save(plan);
    }

    public List<StudyPlan> getStudentPlans(Long studentId) {
        return studyPlanRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    public StudyPlan getById(Long id) {
        return studyPlanRepository.findById(id).orElseThrow();
    }

    @Transactional
    public void delete(Long id) {
        studyPlanRepository.deleteById(id);
    }
}
