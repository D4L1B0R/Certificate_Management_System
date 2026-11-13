package com.example.backendib.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TotpDisableDTO {
    @NotBlank
    private String password;

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    private String code;
}