package com.example.backendib.services;

import com.example.backendib.dtos.certificate.CertificateDetailsDTO;
import com.example.backendib.dtos.certificate.GetCertificateDTO;
import com.example.backendib.models.entities.Certificate;
import com.example.backendib.models.enums.CertificateType;

import java.util.List;

public interface ICertificateQueryService {
    List<GetCertificateDTO> getAllCertificates();
    List<GetCertificateDTO> getMyCertificates(String email);
    CertificateDetailsDTO getCertificateDetails(Long id);
    List<GetCertificateDTO> getAllValidIssuers(CertificateType targetType);
    List<GetCertificateDTO> getCertificatesByOrganization(String email);
    String getCertificatePemById(Long id);
    GetCertificateDTO toDTO(Certificate c);
    GetCertificateDTO mapToDTO(Certificate certificate);
}
