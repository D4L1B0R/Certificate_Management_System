package com.example.backendib.controllers;

import com.example.backendib.dtos.auth.*;
import com.example.backendib.dtos.password.ForgotPasswordRequestDTO;
import com.example.backendib.dtos.password.VerifyRecoveryDTO;
import com.example.backendib.dtos.register.RegisterRequestDTO;
import com.example.backendib.dtos.register.RegisterResponseDTO;
import com.example.backendib.models.entities.User;
import com.example.backendib.repositories.UserRepository;
import com.example.backendib.services.IAdminService;
import com.example.backendib.security.JwtUtil;
import com.example.backendib.services.IAuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {

    private final UserRepository userRepository;
    private final IAuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final IAdminService adminService;

    public AuthController(UserRepository userRepository, IAuthService authService, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, IAdminService adminService) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.adminService = adminService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        try {
            LoginResponseDTO response = authService.authenticate(request);

            if (response.isRequires2fa()) {
                return ResponseEntity.ok(response);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, response.getTokenType() + " " + response.getAccessToken());

            return ResponseEntity.ok().headers(headers).body(response);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/2fa/verify-login")
    public ResponseEntity<LoginResponseDTO> verify2faLogin(@Valid @RequestBody Verify2faRequestDTO request) {
        try {
            LoginResponseDTO response = authService.verify2fa(request.getPendingToken(), request.getCode());

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, response.getTokenType() + " " + response.getAccessToken());
            return ResponseEntity.ok().headers(headers).body(response);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) Map<String, String> body,
                                       HttpServletRequest request) {
        String refreshToken = null;
        if (body != null) refreshToken = body.get("refreshToken");

        if (refreshToken == null && request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                if ("refresh_token".equals(c.getName())) {
                    refreshToken = c.getValue();
                    break;
                }
            }
        }

        // Invalidate server-side refresh token if supported
        authService.invalidateRefreshToken(refreshToken);

        // Clear httpOnly refresh cookie on client
        ResponseCookie cleared = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    @PostMapping("/activate")
    public ResponseEntity<?> activate(
            @RequestParam String token,
            @RequestParam(defaultValue = "password") String mode,
            @RequestBody Map<String, String> body
    ) {

        User user = userRepository.findByActivationToken(token)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token")
                );

        if (user.isActivationUsed()) {
            throw new RuntimeException("Activation token already used");
        }

        if (user.getActivationExpiry() == null || user.getActivationExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Activation token expired");
        }

        if ("crypto".equals(mode)) {

            String decryptedChallenge = body.get("decryptedChallenge");

            if (decryptedChallenge == null) {
                throw new RuntimeException("Missing challenge");
            }

            if (!decryptedChallenge.equals(user.getActivationChallenge())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid challenge - does not match");
            }

            user.setEnabled(true);
        }

        else if ("password".equals(mode)) {

            String password = body.get("password");

            if (password == null) {
                throw new RuntimeException("Missing password");
            }

            user.setPassword(passwordEncoder.encode(password));
            user.setEnabled(true);
        }

        else {
            throw new RuntimeException("Invalid mode");
        }

        user.setActivationUsed(true);
        user.setActivationToken(null);

        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Account activated successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponseDTO> me(Authentication authentication) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        MeResponseDTO dto = new MeResponseDTO();
        dto.setUserId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole().name());

        dto.setOrganizationId(
                user.getOrganization() != null ? user.getOrganization().getId() : null
        );

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(
            @Valid @RequestBody RegisterRequestDTO request
    ) {
        RegisterResponseDTO response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/activation-challenge")
    public ResponseEntity<?> getChallenge(@RequestParam String token) {
        User user = userRepository.findByActivationToken(token)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token")
                );

        return ResponseEntity.ok(Map.of(
                "challenge", user.getEncryptedActivationChallenge()
        ));
    }

    @GetMapping("/organizations")
    public ResponseEntity<?> getAllOrganizations(){
        return ResponseEntity.ok(adminService.getAllOrganizations());
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairDTO> refresh(@RequestBody RefreshTokenRequestDTO req, HttpServletRequest request) {
        String refreshToken = (req != null ? req.getRefreshToken() : null);

        if ((refreshToken == null || refreshToken.isBlank())) {
            String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null && auth.startsWith("Bearer ")) {
                refreshToken = auth.substring(7).trim();
            }
        }

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String subject = jwtUtil.getSubjectIfValidAndRefresh(refreshToken);
        if (subject == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(subject).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        String role = user.getRole().name();

        String newAccess = jwtUtil.generateAccessToken(user.getEmail(), role);
        String newRefresh = jwtUtil.generateRefreshToken(user.getEmail(), role); // rotate

        return ResponseEntity.ok(new TokenPairDTO(newAccess, newRefresh));
    }
      
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequestDTO dto) {
        authService.sendRecoveryEmail(dto.getEmail());
        return ResponseEntity.ok(Map.of("message", "Recovery email sent"));
    }

    @PostMapping("/recover")
    public ResponseEntity<?> recover(@RequestBody VerifyRecoveryDTO dto) {
        authService.verifyRecovery(
                dto.getEmail(),
                dto.getDecryptedChallenge(),
                dto.getNewPassword()
        );
        return ResponseEntity.ok(Map.of(
                "message", "Password changed"
        ));
    }

    @GetMapping("/recovery-challenge")
    public ResponseEntity<?> getRecoveryChallenge(@RequestParam String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(Map.of(
                "challenge", user.getEncryptedRecoveryChallenge()
        ));
    }
}
