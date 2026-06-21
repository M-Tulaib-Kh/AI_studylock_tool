package com.studylock.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class QuizQuestionDto {
    private String id;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctAnswer;
    private int marks;

    public String getOptionText(String option) {
        return switch (option) {
            case "A" -> optionA;
            case "B" -> optionB;
            case "C" -> optionC;
            case "D" -> optionD;
            default -> "";
        };
    }
}
