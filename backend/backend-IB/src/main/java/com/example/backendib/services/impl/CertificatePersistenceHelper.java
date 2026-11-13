package com.example.backendib.services.impl;

import com.example.backendib.models.entities.Certificate;
import com.example.backendib.models.entities.Organization;
import com.example.backendib.models.entities.User;
import com.example.backendib.models.enums.CertificateType;
import com.example.backendib.repositories.CertificateRepository;
import com.example.backendib.services.MasterKeyService;
import com.example.backendib.util.CertificateUtil;
import com.example.backendib.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class CertificatePersistenceHelper {

    private final CertificateRepository certificateRepository;
    private final MasterKeyService masterKeyService;

    public Certificate saveCertificate(
            X509Certificate x509,
            byte[] privateKey,
            CertificateType type,
            Certificate issuer,
            Organization organization,
            User owner
    ) {
        try {
            Certificate cert = baseCert(x509, type, issuer, organization, owner);

            if (privateKey != null) {
                if (organization.getEncryptedOrgKey() == null) {
                    throw new RuntimeException("encryptedOrgKey is NULL for orgId=" + organization.getId());
                }
                byte[] orgKey = masterKeyService.decryptOrgKey(organization).getKey();
                cert.setPrivateKey(EncryptionUtil.encrypt(privateKey, orgKey));
            }

            return certificateRepository.save(cert);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Certificate saveCsrCertificate(
            X509Certificate x509,
            CertificateType type,
            Certificate issuer,
            Organization organization,
            User owner
    ) {
        try {
            return certificateRepository.save(baseCert(x509, type, issuer, organization, owner));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Certificate baseCert(
            X509Certificate x509,
            CertificateType type,
            Certificate issuer,
            Organization organization,
            User owner
    ) throws Exception {
        Certificate cert = new Certificate();
        cert.setCertificatePem(CertificateUtil.toPem(x509));
        cert.setValidFrom(x509.getNotBefore().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        cert.setValidTo(x509.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        cert.setSerialNumber(x509.getSerialNumber().toString());
        cert.setCommonName(extractCommonName(x509));
        cert.setType(type);
        cert.setIssuer(issuer);
        cert.setOrganization(organization);
        cert.setOwner(owner);
        cert.setRevoked(false);
        return cert;
    }

    private String extractCommonName(X509Certificate x509) {
        try {
            X500Name name = new JcaX509CertificateHolder(x509).getSubject();
            RDN[] rdns = name.getRDNs(BCStyle.CN);
            return rdns.length == 0 ? null : IETFUtils.valueToString(rdns[0].getFirst().getValue());
        } catch (Exception e) {
            return null;
        }
    }

    public PrivateKey decryptPrivateKey(Certificate issuerEntity) throws Exception {
        assertKeyUsable(issuerEntity);
        Organization org = issuerEntity.getOrganization();
        try {
            byte[] orgKey = masterKeyService.decryptOrgKey(org).getKey();
            byte[] privateKeyBytes = EncryptionUtil.decrypt(issuerEntity.getPrivateKey(), orgKey);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        } catch (Exception ex) {
            Long orgId = org != null ? org.getId() : null;
            Integer ver = org != null ? org.getEncryptedOrgKeyVersion() : null;
            throw new RuntimeException(
                    "Unable to decrypt issuer private key. Org id=" + orgId +
                            ", orgKeyVersion=" + ver + ". Cause: " + ex.getMessage(), ex);
        }
    }

    public void assertKeyUsable(Certificate cert) {
        if (cert.isRevoked()) {
            throw new RuntimeException("Revoked certificate cannot be used for signing");
        }
        if (cert.getPrivateKey() == null) {
            throw new RuntimeException("No private key available");
        }
    }
}