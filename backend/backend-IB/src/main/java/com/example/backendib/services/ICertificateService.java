package com.example.backendib.services;

import com.example.backendib.dtos.certificate.CertificateDetailsDTO;
import com.example.backendib.dtos.certificate.CreateCertificateDTO;
import com.example.backendib.dtos.certificate.CreateEeCertificateDTO;
import com.example.backendib.dtos.certificate.GetCertificateDTO;
import com.example.backendib.models.entities.Certificate;
import com.example.backendib.models.enums.CertificateType;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.util.List;

public interface ICertificateService {
    Certificate generateRootCertificate(String commonName, String organizationalUnit, String country, Long organizationId, String validFrom, String validTo);
    List<GetCertificateDTO> getMyCertificates(String email);
    List<GetCertificateDTO> getAllCertificates();
    CertificateDetailsDTO getCertificateDetails(Long id);
    GetCertificateDTO issueCertificate(CreateCertificateDTO request);
    GetCertificateDTO issueCertificateFromCsr(PKCS10CertificationRequest csr, Long issuerCertificateId, Long organizationId, String validFrom, String validTo);
    byte[] issueEndEntityAutogenerate(CreateEeCertificateDTO request);
    String getCertificatePemById(Long id);
    boolean isValid(String certData);
    boolean validateById(Long id);
    List<GetCertificateDTO> getCertificatesByOrganization(String email);
    List<GetCertificateDTO> getAllValidIssuers(CertificateType targetType);
    void revokeCertificate(Long id, String reason);
    byte[] pemToDer(String pem) throws Exception;
    byte[] getPkcs12ById(Long id, String password);
}