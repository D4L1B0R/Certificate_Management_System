package com.example.backendib.dtos.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TotpSetupResponseDTO {
    private String qrCodeDataUri;
    private String secret; // for manual entry if QR can't be scanned
}