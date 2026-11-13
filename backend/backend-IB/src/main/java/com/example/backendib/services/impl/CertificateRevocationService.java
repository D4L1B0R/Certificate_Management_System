package com.example.backendib.services.impl;

import com.example.backendib.models.entities.Certificate;
import com.example.backendib.models.enums.RevocationReason;
import com.example.backendib.repositories.CertificateRepository;
import com.example.backendib.services.ICertificateRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CertificateRevocationService implements ICertificateRevocationService {

    private final CertificateRepository certificateRepository;

    @Override
    public void revokeCertificate(Long id, RevocationReason reason) {
        Certificate cert = certificateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));

        if (cert.isRevoked()) {
            throw new RuntimeException("Certificate is already revoked");
        }


        cert.setRevoked(true);
        cert.setRevocationReason(reason);
        cert.setRevocationDate(LocalDateTime.now());

        certificateRepository.save(cert);

        revokeChain(cert);


    }

    private void revokeChain(Certificate cert) {

        List<Certificate> children = certificateRepository.findAll()
                .stream()
                .filter(c -> c.getIssuer() != null && c.getIssuer().getId().equals(cert.getId()))
                .toList();

        for (Certificate child : children) {
            if (!child.isRevoked()) {
                child.setRevoked(true);
                child.setRevocationReason(RevocationReason.CA_COMPROMISE);

                certificateRepository.save(child);

                revokeChain(child);
            }
        }
    }
}
