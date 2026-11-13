package com.example.backendib.services;

import com.example.backendib.dtos.certificate.CreateCertificateDTO;
import com.example.backendib.dtos.certificate.CreateCsrCertificateDTO;
import com.example.backendib.dtos.certificate.CreateEeCertificateDTO;
import com.example.backendib.dtos.certificate.GetCertificateDTO;
import com.example.backendib.models.entities.Certificate;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

public interface ICertificateIssuanceService {
    Certificate generateRootCertificate(
            String commonName,
            String organizationalUnit,
            String country,
            Long organizationId,
            String validFrom,
            String validTo
    );
    GetCertificateDTO issueIntermediateCertificate(CreateCertificateDTO request);
    byte[] issueEndEntityAutogenerate(CreateEeCertificateDTO request);
    GetCertificateDTO issueCertificateFromCsr(CreateCsrCertificateDTO request);
}
