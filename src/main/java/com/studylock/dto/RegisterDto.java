package com.studylock.dto;

import com.studylock.model.UserRole;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class RegisterDto {
    @NotBlank(message = "Name is required")
    private String fullName;

    @Email(message = "Valid email required")
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    private UserRole role = UserRole.STUDENT;
    private String department;
    private String registrationNo;
    private String designation;
    private Integer semester;
}
