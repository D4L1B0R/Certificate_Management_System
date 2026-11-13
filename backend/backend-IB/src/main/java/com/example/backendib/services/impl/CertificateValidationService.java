package com.example.backendib.services.impl;

import com.example.backendib.models.entities.Certificate;
import com.example.backendib.models.enums.CertificateType;
import com.example.backendib.repositories.CertificateRepository;
import com.example.backendib.services.ICertificateCrlService;
import com.example.backendib.services.ICertificateValidationService;
import com.example.backendib.util.CertificateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CertificateValidationService implements ICertificateValidationService {

    private final CertificateRepository certificateRepository;
    private final ICertificateCrlService certificateCrlService;

    @Override
    public boolean validateById(Long id) {
        return certificateRepository.findById(id)
                .map(this::validateCertificate)
                .orElse(false);
    }

    public void validateValidityRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new RuntimeException("ValidFrom and ValidTo are required");
        }
        if (from.isAfter(to)) {
            throw new RuntimeException("Invalid validity range");
        }
    }

    public void validateMaxDuration(LocalDateTime from, LocalDateTime to, CertificateType type) {
        if (type == CertificateType.INTERMEDIATE_CA && to.isAfter(from.plusYears(5))) {
            throw new RuntimeException("Intermediate CA cannot be valid for more than 5 years");
        }
        if (type == CertificateType.END_ENTITY && to.isAfter(from.plusYears(1))) {
            throw new RuntimeException("End-entity certificate cannot be valid for more than 1 year");
        }
    }

    public boolean validateCertificate(Certificate cert) {
        try {
            // 1. revocation check
            boolean revokedInCrl = certificateCrlService.isRevoked(cert.getSerialNumber());

            if (revokedInCrl) {
                return false;
            }

            // 2. time validity check
            LocalDateTime now = LocalDateTime.now();
            if (cert.getValidFrom().isAfter(now) || cert.getValidTo().isBefore(now)) {
                return false;
            }

            // 3. load certificate object
            X509Certificate x509 = CertificateUtil.parseCertificate(cert.getCertificatePem());

            // 4. if root → valid only by time + not revoked
            if (cert.getType() == CertificateType.ROOT_CA) {
                return true;
            }

            // 5. issuer validation
            Certificate issuerEntity = cert.getIssuer();
            if (issuerEntity == null || issuerEntity.isRevoked()) {
                return false;
            }

            X509Certificate issuerCert = CertificateUtil.parseCertificate(issuerEntity.getCertificatePem());

            // 6. issuer time validity
            issuerCert.checkValidity();

            // 7. ca check
            if (issuerCert.getBasicConstraints() == -1) {
                return false;
            }

            // 8. digital signature check
            if (!certSignatureValid(x509, issuerCert)) {
                return false;
            }

            // 9. recursive chain check
            return validateCertificate(issuerEntity);

        } catch (Exception e) {
            System.err.println("Certificate validation error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean certSignatureValid(X509Certificate cert, X509Certificate issuer) {
        try {
            cert.verify(issuer.getPublicKey());
            return true;
        } catch (java.security.SignatureException e) {
            System.err.println("Signature is invalid: " + e.getMessage());
            return false;
        } catch (java.security.InvalidKeyException e) {
            System.err.println("Invalid issuer key: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Signature verification error: " + e.getMessage());
            return false;
        }
    }

    public void validateIssuerCertificate(Certificate issuer, LocalDateTime requestedValidFrom, LocalDateTime requestedValidTo) {

        if (issuer.isRevoked()) {
            throw new RuntimeException("Issuer is revoked");
        }

        LocalDateTime now = LocalDateTime.now();

        if (issuer.getValidFrom().isAfter(now) || issuer.getValidTo().isBefore(now)) {
            throw new RuntimeException("Issuer not valid in time");
        }

        if (issuer.getType() == CertificateType.END_ENTITY) {
            throw new RuntimeException("End-entity cannot be issuer");
        }

        if (requestedValidFrom.isBefore(issuer.getValidFrom())) {
            throw new RuntimeException("Child cert starts before issuer");
        }

        if (requestedValidTo.isAfter(issuer.getValidTo())) {
            throw new RuntimeException("Child cert exceeds issuer validity");
        }

        try {
            X509Certificate issuerX509 = CertificateUtil.parseCertificate(issuer.getCertificatePem());

            if (issuerX509.getBasicConstraints() == -1) {
                throw new RuntimeException("Issuer is not CA");
            }

            boolean[] usage = issuerX509.getKeyUsage();
            if (usage != null && usage.length > 5 && !usage[5]) {
                throw new RuntimeException("Issuer cannot sign certificates");
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Issuer certificate parse failed: " + e.getMessage());
        }
    }

    public void validateChain(Certificate cert) {
        try {
            if (cert.getIssuer() == null) return;

            if (certificateCrlService.isRevoked(cert.getSerialNumber())) {
                throw new RuntimeException("Revoked cert in chain");
            }

            Certificate issuerEntity = cert.getIssuer();

            if (issuerEntity.isRevoked()) {
                throw new RuntimeException("Issuer revoked");
            }

            X509Certificate child = CertificateUtil.parseCertificate(cert.getCertificatePem());
            X509Certificate issuer = CertificateUtil.parseCertificate(issuerEntity.getCertificatePem());

            issuer.checkValidity();

            if (issuer.getBasicConstraints() == -1) {
                throw new RuntimeException("Issuer not CA");
            }

            boolean[] usage = issuer.getKeyUsage();
            if (usage != null && !usage[5]) {
                throw new RuntimeException("Issuer cannot sign");
            }

            child.verify(issuer.getPublicKey());

            validateChain(issuerEntity);

        } catch (Exception e) {
            throw new RuntimeException("Chain invalid: " + e.getMessage());
        }
    }

}
