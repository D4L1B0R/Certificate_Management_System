package com.example.backendib.services.impl;

import com.example.backendib.dtos.auth.LoginRequestDTO;
import com.example.backendib.dtos.auth.LoginResponseDTO;
import com.example.backendib.dtos.register.RegisterRequestDTO;
import com.example.backendib.dtos.register.RegisterResponseDTO;
import com.example.backendib.exceptions.InvalidCredentialsException;
import com.example.backendib.models.entities.Organization;
import com.example.backendib.models.entities.User;
import com.example.backendib.models.enums.Role;
import com.example.backendib.repositories.OrganizationRepository;
import com.example.backendib.repositories.UserRepository;
import com.example.backendib.security.JwtUtil;
import com.example.backendib.services.IAuthService;
import com.example.backendib.services.ICryptoService;
import com.example.backendib.services.IEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final IEmailService emailService;
    private final OrganizationRepository organizationRepository;
    private final ICryptoService cryptoService;
    private final TotpService totpService;

    @Override
    public LoginResponseDTO authenticate(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("User not activated");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (user.isTotpEnabled()) {
            String pendingToken = jwtUtil.generateTotpPendingToken(user.getEmail());
            return LoginResponseDTO.requires2fa(pendingToken);
        }

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), user.getRole().name());

        return LoginResponseDTO.authenticated(accessToken, refreshToken, jwtUtil.getExpirationMs());
    }

    @Override
    public LoginResponseDTO verify2fa(String pendingToken, String code) {
        String email = jwtUtil.getSubjectIfValidTotpPending(pendingToken);
        if (email == null) {
            throw new InvalidCredentialsException("Invalid or expired pending token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        if (!user.isTotpEnabled() || user.getTotpSecret() == null) {
            throw new InvalidCredentialsException("2FA not enabled for this user");
        }

        if (!totpService.isValidCodeForUser(user, code)) {
            throw new InvalidCredentialsException("Invalid code");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), user.getRole().name());

        return LoginResponseDTO.authenticated(accessToken, refreshToken, jwtUtil.getExpirationMs());
    }

    @Override
    public void invalidateRefreshToken(String refreshToken) {
        // TODO: implement refresh token for kt2
    }

    @Override
    public RegisterResponseDTO register(RegisterRequestDTO request) {

        try {

            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new RuntimeException("Email already exists");
            }

            Organization org = organizationRepository.findById(request.getOrganizationId())
                    .orElseThrow(() -> new RuntimeException("Organization not found"));

            User user = new User();

            user.setName(request.getName());
            user.setSurname(request.getSurname());

            user.setEmail(request.getEmail());

            if (!request.getPassword().equals(request.getConfirmPassword())) {
                throw new RuntimeException("Passwords do not match");
            }

            user.setPassword(
                    passwordEncoder.encode(request.getPassword())
            );

            user.setRole(Role.USER);

            user.setEnabled(false);

            user.setPublicKey(request.getPublicKey());

            user.setOrganization(org);

            user.setActivationUsed(false);

            user.setActivationExpiry(
                    LocalDateTime.now().plusHours(24)
            );

            String token = UUID.randomUUID().toString();

            user.setActivationToken(token);

            String challenge = UUID.randomUUID().toString();

            user.setActivationChallenge(challenge);

            String encryptedChallenge =
                    cryptoService.encrypt(
                            user.getPublicKey(),
                            challenge
                    );

            user.setEncryptedActivationChallenge(encryptedChallenge);

            userRepository.save(user);

            emailService.sendActivationEmail(
                    user.getEmail(),
                    token,
                    "crypto"
            );

            return new RegisterResponseDTO(
                    user.getId(),
                    user.getEmail(),
                    "Activation email sent"
            );

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void sendRecoveryEmail(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String challenge = UUID.randomUUID().toString();

        user.setRecoveryChallenge(challenge);
        user.setRecoveryUsed(false);
        user.setRecoveryExpiry(LocalDateTime.now().plusMinutes(15));

        try {
            String encrypted = cryptoService.encrypt(
                    user.getPublicKey(),
                    challenge
            );

            user.setEncryptedRecoveryChallenge(encrypted);

        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }

        userRepository.save(user);

        emailService.sendRecoveryEmail(user.getEmail(), user.getEncryptedRecoveryChallenge());
    }

    @Override
    public void verifyRecovery(String email, String decryptedChallenge, String newPassword) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isRecoveryUsed()) {
            throw new RuntimeException("Recovery already used");
        }

        if (user.getRecoveryExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Recovery expired");
        }

        if (!decryptedChallenge.equals(user.getRecoveryChallenge())) {
            throw new RuntimeException("Invalid challenge");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setRecoveryUsed(true);
        user.setRecoveryChallenge(null);

        userRepository.save(user);
    }
}
