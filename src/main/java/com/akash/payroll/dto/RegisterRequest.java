package com.akash.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 255)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 72, message = "Password must contain 6 to 72 characters")
    private String password;
}
