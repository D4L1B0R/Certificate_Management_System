package com.example.backendib.dtos.certificate;

import com.example.backendib.models.enums.CertificateType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GetCertificateDTO {
    private Long id;
    private String subjectCN;
    private String issuerCN;
    private CertificateType type;
    private LocalDateTime  validFrom;
    private LocalDateTime validTo;
    private String serialNumber;
    private boolean revoked;
    private boolean valid;
    private boolean hasPrivateKey;
}