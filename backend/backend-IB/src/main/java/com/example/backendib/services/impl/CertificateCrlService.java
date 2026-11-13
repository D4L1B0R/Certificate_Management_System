package com.example.backendib.services.impl;

import com.example.backendib.dtos.certificate.CrlEntryDTO;
import com.example.backendib.models.entities.Certificate;
import com.example.backendib.repositories.CertificateRepository;
import com.example.backendib.services.ICertificateCrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.Serial;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CertificateCrlService implements ICertificateCrlService {
    private final CertificateRepository certificateRepository;

    public List<CrlEntryDTO> getCRL() {

        return certificateRepository.findAll()
                .stream()
                .filter(Certificate::isRevoked)
                .map(cert -> new CrlEntryDTO(
                        cert.getSerialNumber(),
                        cert.getRevocationDate(),
                        cert.getRevocationReason()
                ))
                .toList();
    }

    public boolean isRevoked(String serialNumber) {
        return certificateRepository.findBySerialNumber(serialNumber)
                .map(Certificate::isRevoked)
                .orElse(false);
    }
}
