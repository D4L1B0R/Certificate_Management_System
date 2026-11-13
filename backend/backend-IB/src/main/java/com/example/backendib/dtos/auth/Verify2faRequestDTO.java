package com.example.backendib.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Verify2faRequestDTO {
    @NotBlank
    private String pendingToken;

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    private String code;
}