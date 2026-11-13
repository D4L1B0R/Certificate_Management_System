package com.example.backendib.services;

import com.example.backendib.models.enums.RevocationReason;

public interface ICertificateRevocationService {
    void revokeCertificate(Long id, RevocationReason reason);
}
