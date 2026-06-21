package com.studylock.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class TutorMessageDto {
    private String subject;
    private String message;
    private List<Map<String,Object>> history;
}
