package com.example.backendib.services.impl;

import com.example.backendib.models.entities.Certificate;
import com.example.backendib.repositories.CertificateRepository;
import com.example.backendib.services.IKeystoreService;
import com.example.backendib.util.CertificateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KeystoreService implements IKeystoreService {

    private final CertificateRepository certificateRepository;
    private final CertificatePersistenceHelper certificatePersistenceHelper;

    public byte[] buildPkcs12Keystore(
            X509Certificate eeCert,
            PrivateKey eePrivateKey,
            Certificate issuerEntity,
            String password
    ) throws Exception {
        List<X509Certificate> chain = new java.util.ArrayList<>();
        chain.add(eeCert);

        Certificate current = issuerEntity;
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        while (current != null) {
            X509Certificate c = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(current.getCertificatePem().getBytes())
            );
            chain.add(c);
            current = current.getIssuer();
        }

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(
                eeCert.getSubjectX500Principal().getName(),
                eePrivateKey,
                password.toCharArray(),
                chain.toArray(new X509Certificate[0])
        );

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        ks.store(baos, password.toCharArray());
        return baos.toByteArray();
    }

    public byte[] getPkcs12ById(Long id, String password) {
        try {
            Certificate certEntity = certificateRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Certificate not found"));

            if (certEntity.getPrivateKey() == null || certEntity.getPrivateKey().length == 0) {
                throw new IllegalStateException("Certificate has no private key");
            }

            X509Certificate cert = CertificateUtil.parseCertificate(certEntity.getCertificatePem());
            PrivateKey privateKey = certificatePersistenceHelper.decryptPrivateKey(certEntity);

            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);

            String alias = "cert-" + certEntity.getId();
            char[] pwd = (password == null ? "changeit" : password).toCharArray();

            ks.setKeyEntry(alias, privateKey, pwd, new java.security.cert.Certificate[]{cert});

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ks.store(baos, pwd);
                return baos.toByteArray();
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PKCS12: " + e.getMessage(), e);
        }
    }

    public byte[] pemToDer(String pem) throws Exception {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("PEM is empty");
        }

        String base64 = pem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");

        if (base64.isEmpty()) {
            throw new IllegalArgumentException("PEM contains no data");
        }

        return java.util.Base64.getDecoder().decode(base64);
    }
}
