package com.studylock.dto;

import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class SessionStartDto {
    private int durationMinutes;
    private boolean cameraEnabled;
    private List<String> blockedApps;
}
