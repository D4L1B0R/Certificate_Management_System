package com.example.backendib.services;

import com.example.backendib.models.entities.Certificate;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public interface IKeystoreService {
    byte[] buildPkcs12Keystore(
            X509Certificate eeCert,
            PrivateKey eePrivateKey,
            Certificate issuerEntity,
            String password) throws Exception;
    byte[] getPkcs12ById(Long id, String password);
    byte[] pemToDer(String pem) throws Exception;
}
