package com.example.backendib.services;

import com.example.backendib.models.entities.Certificate;
import com.example.backendib.models.enums.CertificateType;

import java.security.cert.X509Certificate;
import java.time.LocalDateTime;

public interface ICertificateValidationService {
    boolean validateById(Long id);
    void validateValidityRange(LocalDateTime from, LocalDateTime to);
    void validateMaxDuration(LocalDateTime from, LocalDateTime to, CertificateType type);
    boolean validateCertificate(Certificate cert);
    boolean certSignatureValid(X509Certificate cert, X509Certificate issuer);
    void validateIssuerCertificate(Certificate issuer, LocalDateTime requestedValidFrom, LocalDateTime requestedValidTo);
    void validateChain(Certificate cert);
}
