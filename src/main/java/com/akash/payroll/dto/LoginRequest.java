package com.akash.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Username is required")
    @jakarta.validation.constraints.Size(max = 255)
    private String username;

    @NotBlank(message = "Password is required")
    @jakarta.validation.constraints.Size(max = 1024)
    private String password;
}
