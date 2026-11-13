package com.example.backendib.dtos.password;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class VerifyRecoveryDTO {
    private String email;
    private String decryptedChallenge;
    private String newPassword;
}
