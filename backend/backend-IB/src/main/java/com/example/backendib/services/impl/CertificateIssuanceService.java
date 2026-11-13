package com.example.backendib.services.impl;

import com.example.backendib.dtos.certificate.CreateCertificateDTO;
import com.example.backendib.dtos.certificate.CreateCsrCertificateDTO;
import com.example.backendib.dtos.certificate.CreateEeCertificateDTO;
import com.example.backendib.dtos.certificate.GetCertificateDTO;
import com.example.backendib.models.entities.Certificate;
import com.example.backendib.models.entities.Organization;
import com.example.backendib.models.entities.User;
import com.example.backendib.models.enums.CertificateType;
import com.example.backendib.models.enums.Role;
import com.example.backendib.repositories.CertificateRepository;
import com.example.backendib.repositories.OrganizationRepository;
import com.example.backendib.repositories.UserRepository;
import com.example.backendib.services.ICertificateIssuanceService;
import com.example.backendib.services.ICertificateQueryService;
import com.example.backendib.services.ICertificateValidationService;
import com.example.backendib.services.IKeystoreService;
import com.example.backendib.util.AuthUtils;
import com.example.backendib.util.CertificateUtil;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.CRLDistPoint;

@Service
@RequiredArgsConstructor
public class CertificateIssuanceService implements ICertificateIssuanceService {

    @Value("${app.crl-url}")
    private String crlUrl;

    private final UserRepository userRepository;
    private final CertificateRepository certificateRepository;
    private final OrganizationRepository organizationRepository;

    private final IKeystoreService keystoreService;
    private final ICertificateQueryService certificateQueryService;
    private final ICertificateValidationService certificateValidationService;
    private final CertificatePersistenceHelper certificatePersistenceHelper;

    @Override
    public Certificate generateRootCertificate(
            String commonName,
            String organizationalUnit,
            String country,
            Long organizationId,
            String validFrom,
            String validTo
    ) {
        try {
            LocalDateTime from = LocalDateTime.parse(validFrom);
            LocalDateTime to = LocalDateTime.parse(validTo);

            if (from.isAfter(to)) {
                throw new RuntimeException("ValidFrom must be before ValidTo");
            }

            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            Organization org = organizationRepository.findById(organizationId)
                    .orElseThrow(() -> new RuntimeException("Organization not found"));

            X500Name subject = new X500Name(
                    "CN=" + commonName +
                            ", O=" + org.getName() +
                            ", OU=" + organizationalUnit +
                            ", C=" + country
            );

            BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());

            Date notBefore = java.sql.Timestamp.valueOf(from);
            Date notAfter = java.sql.Timestamp.valueOf(to);

            JcaX509v3CertificateBuilder builder =
                    new JcaX509v3CertificateBuilder(
                            subject,
                            serial,
                            notBefore,
                            notAfter,
                            subject,
                            keyPair.getPublic()
                    );
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            builder.addExtension(Extension.cRLDistributionPoints, false, buildCrlDistPoint());

            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                    .build(keyPair.getPrivate());

            X509Certificate x509 = new JcaX509CertificateConverter()
                    .getCertificate(builder.build(signer));

            User owner = userRepository.findByEmail(AuthUtils.getCurrentUserEmail())
                    .orElseThrow(() -> new RuntimeException("Current user not found"));

            return certificatePersistenceHelper.saveCertificate(
                    x509,
                    keyPair.getPrivate().getEncoded(),
                    CertificateType.ROOT_CA,
                    null,
                    org,
                    owner
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public GetCertificateDTO issueIntermediateCertificate(CreateCertificateDTO request) {
        try {
            certificateValidationService.validateValidityRange(request.getValidFrom(), request.getValidTo());
            certificateValidationService.validateMaxDuration(request.getValidFrom(), request.getValidTo(), CertificateType.INTERMEDIATE_CA);

            if (request.getIssuerCertificateId() == null) {
                throw new RuntimeException("Issuer certificate is required");
            }
            Certificate issuer = certificateRepository.findById(request.getIssuerCertificateId())
                    .orElseThrow(() -> new RuntimeException("Issuer not found"));

            certificateValidationService.validateIssuerCertificate(issuer, request.getValidFrom(), request.getValidTo());
            certificateValidationService.validateChain(issuer);

            User currentUser = userRepository.findByEmail(AuthUtils.getCurrentUserEmail())
                    .orElseThrow(() -> new RuntimeException("Current user not found"));

            Organization org;
            if (currentUser.getRole() == Role.CA) {
                if (currentUser.getOrganization() == null) {
                    throw new RuntimeException("CA user has no organization");
                }
                org = currentUser.getOrganization();
            } else {
                org = organizationRepository.findById(request.getOrganizationId())
                        .orElseThrow(() -> new RuntimeException("Organization not found"));
            }

            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            X500Name subject = CertificateUtil.buildX500Name(request, org.getName());
            X500Name issuerName = CertificateUtil.getX500NameFromCert(issuer);
            BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
            Date from = Date.from(request.getValidFrom().atZone(ZoneId.systemDefault()).toInstant());
            Date to = Date.from(request.getValidTo().atZone(ZoneId.systemDefault()).toInstant());

            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuerName, serial, from, to, subject, keyPair.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            builder.addExtension(Extension.cRLDistributionPoints, false, buildCrlDistPoint());

            PrivateKey signingKey = certificatePersistenceHelper.decryptPrivateKey(issuer);
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(signingKey);
            X509Certificate x509 = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

            Certificate saved = certificatePersistenceHelper.saveCertificate(
                    x509,
                    keyPair.getPrivate().getEncoded(),
                    CertificateType.INTERMEDIATE_CA,
                    issuer,
                    org,
                    null
            );

            return certificateQueryService.toDTO(saved);

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public byte[] issueEndEntityAutogenerate(CreateEeCertificateDTO request) {
        try {
            Certificate issuer = certificateRepository.findById(request.getIssuerCertificateId())
                    .orElseThrow(() -> new RuntimeException("Issuer not found"));

            certificateValidationService.validateIssuerCertificate(issuer, request.getValidFrom(), request.getValidTo());

            if (request.getValidFrom().isAfter(request.getValidTo())) {
                throw new RuntimeException("Invalid date range");
            }

            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            Organization org = issuer.getOrganization();

            X500Name subject = CertificateUtil.buildX500Name(request, org.getName());
            X500Name issuerName = CertificateUtil.getX500NameFromCert(issuer);

            BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
            Date from = Date.from(request.getValidFrom().atZone(ZoneId.systemDefault()).toInstant());
            Date to = Date.from(request.getValidTo().atZone(ZoneId.systemDefault()).toInstant());

            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuerName, serial, from, to, subject, keyPair.getPublic()
            );
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

            int usage = 0;
            if (Boolean.TRUE.equals(request.getDigitalSignature()))
                usage |= KeyUsage.digitalSignature;
            if (Boolean.TRUE.equals(request.getKeyEncipherment()))
                usage |= KeyUsage.keyEncipherment;
            if (usage == 0) {
                usage = KeyUsage.digitalSignature | KeyUsage.keyEncipherment;
            }
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(usage));

            PrivateKey signingKey = certificatePersistenceHelper.decryptPrivateKey(issuer);
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(signingKey);
            X509Certificate x509 = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

            certificateValidationService.validateChain(issuer);

            String email = AuthUtils.getCurrentUserEmail();
            User owner = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Current user not found"));

            certificatePersistenceHelper.saveCertificate(
                    x509,
                    null,
                    CertificateType.END_ENTITY,
                    issuer,
                    org,
                    owner
            );

            return keystoreService.buildPkcs12Keystore(
                    x509,
                    keyPair.getPrivate(),
                    issuer,
                    request.getKeystorePassword()
            );

        } catch (Exception e) {
            System.err.println("ERROR in issueEndEntityAutogenerate:");
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public GetCertificateDTO issueCertificateFromCsr(CreateCsrCertificateDTO request) {
        try {
            PKCS10CertificationRequest csr = CertificateUtil.parseCsr(request.getCsrPem());

            var verifierProvider = new JcaContentVerifierProviderBuilder().build(csr.getSubjectPublicKeyInfo());
            if (!csr.isSignatureValid(verifierProvider)) {
                throw new RuntimeException("CSR signature invalid (proof of possession failed)");
            }

            Certificate issuer = certificateRepository.findById(request.getIssuerCertificateId())
                    .orElseThrow(() -> new RuntimeException("Issuer not found"));

            LocalDateTime from = LocalDateTime.parse(request.getValidFrom());
            LocalDateTime to = LocalDateTime.parse(request.getValidTo());

            if (from.isAfter(to) /*|| if (from.isBefore(LocalDateTime.now()))*/) {
                throw new RuntimeException("Invalid validity range");
            }

            certificateValidationService.validateIssuerCertificate(issuer, from, to);
            certificateValidationService.validateChain(issuer);

            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(csr.getSubjectPublicKeyInfo().getEncoded()));

            X500Name subject = csr.getSubject();
            X500Name issuerName = CertificateUtil.getX500NameFromCert(issuer);
            BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
            Date fromDate = Date.from(from.atZone(ZoneId.systemDefault()).toInstant());
            Date toDate = Date.from(to.atZone(ZoneId.systemDefault()).toInstant());

            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuerName, serial, fromDate, toDate, subject, publicKey
            );
            builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
            builder.addExtension(Extension.cRLDistributionPoints, false, buildCrlDistPoint());

            PrivateKey signingKey = certificatePersistenceHelper.decryptPrivateKey(issuer);
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(signingKey);
            X509Certificate x509 = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

            Organization org = issuer.getOrganization();

            User owner = userRepository.findByEmail(AuthUtils.getCurrentUserEmail())
                    .orElseThrow(() -> new RuntimeException("Current user not found"));

            Certificate saved = certificatePersistenceHelper.saveCsrCertificate(
                    x509,
                    CertificateType.END_ENTITY,
                    issuer,
                    org,
                    owner
            );

            return certificateQueryService.toDTO(saved);

        } catch (Exception e) {
            throw new RuntimeException("CSR issue failed: " + e.getMessage(), e);
        }
    }

    private CRLDistPoint buildCrlDistPoint() {
        GeneralName gn = new GeneralName(GeneralName.uniformResourceIdentifier, crlUrl);
        DistributionPoint dp = new DistributionPoint(
                new DistributionPointName(new GeneralNames(gn)), null, null);
        return new CRLDistPoint(new DistributionPoint[]{dp});
    }
}