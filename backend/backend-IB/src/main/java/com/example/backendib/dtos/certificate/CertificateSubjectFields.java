package com.example.backendib.dtos.certificate;

public interface CertificateSubjectFields {
    String getCommonName();
    String getOrganizationalUnit();
    String getCountry();
    String getEmail();
    String getState();
    String getLocality();
}