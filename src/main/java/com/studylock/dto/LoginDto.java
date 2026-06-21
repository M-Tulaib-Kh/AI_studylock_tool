package com.studylock.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class LoginDto {
    @Email @NotBlank
    private String email;
    @NotBlank
    private String password;
}
