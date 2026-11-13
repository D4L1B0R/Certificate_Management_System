package com.example.backendib.util;

import com.example.backendib.dtos.certificate.CertificateSubjectFields;
import com.example.backendib.models.entities.Certificate;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class CertificateUtil {

    public static String toPem(X509Certificate cert) throws Exception {
        StringWriter sw = new StringWriter();
        try (PemWriter pw = new PemWriter(sw)) {
            pw.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
        }
        return sw.toString();
    }

    public static X509Certificate parseCertificate(String pem) throws Exception {
        if (pem == null || pem.isBlank()) {
            throw new java.security.cert.CertificateException("Empty input");
        }

        String trimmed = pem.trim();
        byte[] der;

        if (trimmed.contains("-----BEGIN CERTIFICATE-----")) {
            String base64 = trimmed
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            if (base64.isEmpty()) {
                throw new java.security.cert.CertificateException("Empty input");
            }
            der = java.util.Base64.getDecoder().decode(base64);
        } else {
            // If it's not PEM, assume it's already DER in binary-like string (best-effort)
            der = trimmed.getBytes(StandardCharsets.ISO_8859_1);
            if (der.length == 0) {
                throw new java.security.cert.CertificateException("Empty input");
            }
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
    }

    public static PKCS10CertificationRequest parseCsr(String csrPem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(csrPem))) {
            if (parser.readObject() instanceof PKCS10CertificationRequest csr) {
                return csr;
            }
            throw new IllegalArgumentException("Not a valid PKCS#10 CSR");
        }
    }

    public static X500Name buildX500Name(CertificateSubjectFields fields, String orgName) {
        StringBuilder dn = new StringBuilder();
        dn.append("CN=").append(fields.getCommonName());
        dn.append(", O=").append(orgName);
        dn.append(", OU=").append(fields.getOrganizationalUnit());
        dn.append(", C=").append(fields.getCountry());

        if (fields.getState() != null && !fields.getState().isEmpty()) {
            dn.append(", ST=").append(fields.getState());
        }
        if (fields.getLocality() != null && !fields.getLocality().isEmpty()) {
            dn.append(", L=").append(fields.getLocality());
        }
        if (fields.getEmail() != null && !fields.getEmail().isEmpty()) {
            dn.append(", emailAddress=").append(fields.getEmail());
        }

        return new X500Name(dn.toString());
    }

    public static X500Name getX500NameFromCert(Certificate issuerEntity) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate x509 = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(issuerEntity.getCertificatePem().getBytes()));
        return X500Name.getInstance(x509.getSubjectX500Principal().getEncoded());
    }

}