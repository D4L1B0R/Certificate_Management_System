package com.example.backendib.services;

public interface IEmailService {
    void sendActivationEmail(String toEmail, String token, String mode);
    void sendRecoveryEmail(String toEmail, String encryptedChallenge);
}
