package com.example.backendib.dtos.certificate;

import com.example.backendib.models.enums.CertificateType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CertificateDetailsDTO {
    private Long id;
    private String subjectCN;
    private String organizationalUnit;
    private String country;
    private String issuerCN;
    private CertificateType type;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String serialNumber;
    private boolean revoked;
    private String certificatePem;

    private String signatureAlgorithm;
    private String publicKeyAlgorithm;
    private int publicKeySize;
    private String subjectDN;
    private String issuerDN;
}