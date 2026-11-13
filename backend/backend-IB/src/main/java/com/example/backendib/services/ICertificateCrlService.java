package com.example.backendib.services;

import com.example.backendib.dtos.certificate.CrlEntryDTO;

import java.util.List;

public interface ICertificateCrlService {
    List<CrlEntryDTO> getCRL();
    boolean isRevoked(String serialNumber);
}
