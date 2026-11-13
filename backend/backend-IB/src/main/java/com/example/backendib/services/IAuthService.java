package com.example.backendib.services;

import com.example.backendib.dtos.auth.LoginRequestDTO;
import com.example.backendib.dtos.auth.LoginResponseDTO;
import com.example.backendib.dtos.register.RegisterRequestDTO;
import com.example.backendib.dtos.register.RegisterResponseDTO;

public interface IAuthService {
    LoginResponseDTO authenticate(LoginRequestDTO request);
    void invalidateRefreshToken(String refreshToken);
    RegisterResponseDTO register(RegisterRequestDTO request);
    void sendRecoveryEmail(String email);
    void verifyRecovery(String email, String decryptedChallenge, String newPassword);
    LoginResponseDTO verify2fa(String pendingToken, String code);
}
