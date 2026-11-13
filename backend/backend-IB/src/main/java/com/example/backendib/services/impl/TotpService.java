package com.example.backendib.services.impl;

import com.example.backendib.dtos.auth.TotpSetupResponseDTO;
import com.example.backendib.models.entities.Organization;
import com.example.backendib.models.entities.User;
import com.example.backendib.repositories.UserRepository;
import com.example.backendib.services.MasterKeyService;
import com.example.backendib.util.EncryptionUtil;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TotpService {

    private static final String ISSUER = "IB-PKI";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MasterKeyService masterKeyService;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier = buildVerifier();

    private static CodeVerifier buildVerifier() {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        DefaultCodeVerifier v = new DefaultCodeVerifier(codeGenerator, timeProvider);
        v.setTimePeriod(30);
        v.setAllowedTimePeriodDiscrepancy(1);
        return v;
    }

    public TotpSetupResponseDTO setupForUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isTotpEnabled()) {
            throw new RuntimeException("2FA is already enabled");
        }

        String secret = secretGenerator.generate();
        user.setTotpSecret(encryptSecret(secret, user.getOrganization()));
        userRepository.save(user);

        String qrUri = generateQrDataUri(email, secret);
        return new TotpSetupResponseDTO(qrUri, secret);
    }

    public void verifySetupForUser(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getTotpSecret() == null) {
            throw new RuntimeException("No pending 2FA setup. Call /2fa/setup first.");
        }
        if (user.isTotpEnabled()) {
            throw new RuntimeException("2FA is already enabled");
        }

        String secret = decryptSecret(user.getTotpSecret(), user.getOrganization());
        if (!isValidCode(secret, code)) {
            throw new RuntimeException("Invalid code");
        }

        user.setTotpEnabled(true);
        userRepository.save(user);
    }

    public void disableForUser(String email, String password, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isTotpEnabled()) {
            throw new RuntimeException("2FA is not enabled");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        String secret = decryptSecret(user.getTotpSecret(), user.getOrganization());
        if (!isValidCode(secret, code)) {
            throw new RuntimeException("Invalid code");
        }

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
    }

    public boolean isEnabledForUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.isTotpEnabled();
    }

    public boolean isValidCodeForUser(User user, String code) {
        if (user.getTotpSecret() == null || code == null) return false;
        String secret = decryptSecret(user.getTotpSecret(), user.getOrganization());
        return isValidCode(secret, code);
    }

    private boolean isValidCode(String secret, String code) {
        if (secret == null || code == null) return false;
        return codeVerifier.isValidCode(secret, code);
    }

    private String encryptSecret(String plaintextSecret, Organization org) {
        try {
            if (org == null || org.getEncryptedOrgKey() == null) {
                throw new RuntimeException("User has no organization with encryption key");
            }
            Map.Entry<byte[], Integer> dec = masterKeyService.decryptOrgKey(org);
            byte[] orgKey = dec.getKey();
            byte[] encrypted = EncryptionUtil.encrypt(
                    plaintextSecret.getBytes(StandardCharsets.UTF_8), orgKey);
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt TOTP secret", e);
        }
    }

    private String decryptSecret(String encryptedBase64, Organization org) {
        try {
            if (org == null || org.getEncryptedOrgKey() == null) {
                throw new RuntimeException("User has no organization with encryption key");
            }
            Map.Entry<byte[], Integer> dec = masterKeyService.decryptOrgKey(org);
            byte[] orgKey = dec.getKey();
            byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);
            byte[] decrypted = EncryptionUtil.decrypt(encrypted, orgKey);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt TOTP secret", e);
        }
    }

    private String generateQrDataUri(String email, String secret) {
        try {
            QrData data = new QrData.Builder()
                    .label(email)
                    .secret(secret)
                    .issuer(ISSUER)
                    .algorithm(HashingAlgorithm.SHA1)
                    .digits(6)
                    .period(30)
                    .build();

            byte[] qrPng = qrGenerator.generate(data);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(qrPng);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
}