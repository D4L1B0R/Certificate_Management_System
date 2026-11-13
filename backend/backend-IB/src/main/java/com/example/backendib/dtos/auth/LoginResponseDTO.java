package com.example.backendib.dtos.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class LoginResponseDTO {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn; // milliseconds until expiration (from application properties)

    private boolean requires2fa;
    private String pendingToken;

    public static LoginResponseDTO requires2fa(String pendingToken) {
        LoginResponseDTO r = new LoginResponseDTO();
        r.setRequires2fa(true);
        r.setPendingToken(pendingToken);
        return r;
    }

    public static LoginResponseDTO authenticated(String access, String refresh, long expiresIn) {
        LoginResponseDTO r = new LoginResponseDTO();
        r.setAccessToken(access);
        r.setRefreshToken(refresh);
        r.setTokenType("Bearer");
        r.setExpiresIn(expiresIn);
        return r;
    }
}
