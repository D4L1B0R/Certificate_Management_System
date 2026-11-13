package com.example.backendib.controllers;

import com.example.backendib.dtos.auth.TotpDisableDTO;
import com.example.backendib.dtos.auth.TotpSetupResponseDTO;
import com.example.backendib.dtos.auth.TotpVerifyDTO;
import com.example.backendib.models.entities.User;
import com.example.backendib.repositories.UserRepository;
import com.example.backendib.services.impl.TotpService;
import com.example.backendib.util.AuthUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final TotpService totpService;

    @PostMapping("/2fa/setup")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TotpSetupResponseDTO> setupTotp() {
        String email = AuthUtils.getCurrentUserEmail();
        return ResponseEntity.ok(totpService.setupForUser(email));
    }

    @PostMapping("/2fa/verify-setup")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> verifySetup(@Valid @RequestBody TotpVerifyDTO dto) {
        String email = AuthUtils.getCurrentUserEmail();
        totpService.verifySetupForUser(email, dto.getCode());
        return ResponseEntity.ok(Map.of("message", "2FA enabled"));
    }

    @PostMapping("/2fa/disable")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> disableTotp(@Valid @RequestBody TotpDisableDTO dto) {
        String email = AuthUtils.getCurrentUserEmail();
        totpService.disableForUser(email, dto.getPassword(), dto.getCode());
        return ResponseEntity.ok(Map.of("message", "2FA disabled"));
    }

    @GetMapping("/2fa/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> totpStatus() {
        String email = AuthUtils.getCurrentUserEmail();
        return ResponseEntity.ok(Map.of("enabled", totpService.isEnabledForUser(email)));
    }
}
