package com.studylock.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class GradeDto {
    @NotNull @Min(0)
    private Integer marks;
    @NotBlank
    private String feedback;
}
