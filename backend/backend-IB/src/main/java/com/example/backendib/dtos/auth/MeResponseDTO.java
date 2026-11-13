package com.example.backendib.dtos.auth;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MeResponseDTO {
    private Long userId;
    private String email;
    private String role;
    private Long organizationId;
}
