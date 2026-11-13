package com.example.backendib.services.impl;

import com.example.backendib.dtos.certificate.CertificateDetailsDTO;
import com.example.backendib.dtos.certificate.GetCertificateDTO;
import com.example.backendib.models.entities.Certificate;
import com.example.backendib.models.entities.User;
import com.example.backendib.models.enums.CertificateType;
import com.example.backendib.repositories.CertificateRepository;
import com.example.backendib.repositories.UserRepository;
import com.example.backendib.services.ICertificateQueryService;
import com.example.backendib.services.ICertificateValidationService;
import com.example.backendib.util.CertificateUtil;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CertificateQueryService implements ICertificateQueryService {

    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;

    private final ICertificateValidationService certificateValidationService;

    private static final Logger log = LoggerFactory.getLogger(CertificateQueryService.class);

    @Override
    public List<GetCertificateDTO> getAllCertificates() {
        return certificateRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public List<GetCertificateDTO> getMyCertificates(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return certificateRepository.findByOwnerAndType(user, CertificateType.END_ENTITY)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public CertificateDetailsDTO getCertificateDetails(Long id) {
        Certificate cert = certificateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));

        CertificateDetailsDTO dto = new CertificateDetailsDTO();
        dto.setId(cert.getId());
        dto.setSubjectCN(cert.getCommonName());
        dto.setIssuerCN(cert.getIssuer() != null ? cert.getIssuer().getCommonName() : null);
        dto.setType(cert.getType());
        dto.setValidFrom(cert.getValidFrom());
        dto.setValidTo(cert.getValidTo());
        dto.setSerialNumber(cert.getSerialNumber());
        dto.setRevoked(cert.isRevoked());
        dto.setCertificatePem(cert.getCertificatePem());

        extractFromPem(cert.getCertificatePem(), dto);

        return dto;
    }

    private void extractFromPem(String pem, CertificateDetailsDTO dto) {
        if (pem == null || pem.isBlank()) return;

        try {
            X509Certificate x509 = CertificateUtil.parseCertificate(pem);

            dto.setSignatureAlgorithm(x509.getSigAlgName());
            dto.setPublicKeyAlgorithm(x509.getPublicKey().getAlgorithm());
            dto.setSubjectDN(x509.getSubjectX500Principal().getName());
            dto.setIssuerDN(x509.getIssuerX500Principal().getName());

            X500Name subject = new JcaX509CertificateHolder(x509).getSubject();
            dto.setOrganizationalUnit(getRdn(subject, BCStyle.OU));
            dto.setCountry(getRdn(subject, BCStyle.C));

            if (x509.getPublicKey() instanceof RSAPublicKey rsaKey) {
                dto.setPublicKeySize(rsaKey.getModulus().bitLength());
            }
        } catch (Exception e) {
            // pem parsing failed
        }
    }

    private static String getRdn(X500Name name, ASN1ObjectIdentifier oid) {
        RDN[] rdns = name.getRDNs(oid);
        return rdns.length == 0 ? null : IETFUtils.valueToString(rdns[0].getFirst().getValue());
    }

    @Override
    public List<GetCertificateDTO> getAllValidIssuers(CertificateType targetType) {
        LocalDateTime now = LocalDateTime.now();

        return certificateRepository.findByRevokedFalse().stream()
                .filter(c -> c.getValidFrom().isBefore(now))
                .filter(c -> c.getValidTo().isAfter(now))
                .filter(c -> {
                    if (targetType == CertificateType.INTERMEDIATE_CA) {
                        return c.getType() == CertificateType.ROOT_CA
                                || c.getType() == CertificateType.INTERMEDIATE_CA;
                    }
                    if (targetType == CertificateType.END_ENTITY) {
                        return c.getType() == CertificateType.ROOT_CA
                                || c.getType() == CertificateType.INTERMEDIATE_CA;
                    }
                    return false;
                })
                .map(this::mapToDTO)
                .toList();
    }

    @Override
    public List<GetCertificateDTO> getCertificatesByOrganization(String email) {
        log.debug("getCertificatesByOrganization called with email param='{}'", email);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            log.debug("Authenticated principal='{}', authorities={}", auth.getName(), auth.getAuthorities());
        } else {
            log.debug("No Authentication found in SecurityContext");
        }

        try {
            User user;
            boolean usedFallback = false;
            try {
                user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("User not found"));
                log.debug("findByEmail returned single user id={}, email={}", user.getId(), user.getEmail());
            } catch (org.springframework.dao.IncorrectResultSizeDataAccessException e) {
                usedFallback = true;
                log.warn("findByEmail returned non-unique result for email='{}' - using fallback scan. Exception: {}", email, e.getMessage());
                user = userRepository.findAll()
                        .stream()
                        .filter(u -> u.getEmail() != null && u.getEmail().equals(email))
                        .findFirst()
                        .orElseThrow(() -> {
                            RuntimeException re = new RuntimeException("User lookup returned multiple results and fallback failed", e);
                            log.error("Fallback user lookup failed for email='{}'", email, re);
                            return re;
                        });
                log.debug("Fallback resolved user id={}, email={}", user.getId(), user.getEmail());
            }

            if (user.getOrganization() == null) {
                log.warn("User (id={}) has no organization", user.getId());
                throw new RuntimeException("User has no organization");
            }

            Long orgId = user.getOrganization().getId();
            String orgName = user.getOrganization().getName();
            log.debug("Resolved user (id={}) organization id={}, name='{}' (usedFallback={})", user.getId(), orgId, orgName, usedFallback);

            List<Certificate> certs = certificateRepository.findAllByOrganizationId(orgId);
            log.debug("Found {} certificates for orgId={}", certs.size(), orgId);

            if (!certs.isEmpty()) {
                String brief = certs.stream()
                        .map(c -> String.format("[id=%d,name=%s,certOrgId=%s,certOrgName=%s]",
                                c.getId(),
                                c.getCommonName(),
                                c.getOrganization() != null ? c.getOrganization().getId() : null,
                                c.getOrganization() != null ? c.getOrganization().getName() : null))
                        .collect(Collectors.joining(", "));
                log.debug("Certificates detail for orgId={}: {}", orgId, brief);

                // detect unexpected certificates whose stored orgId differs from requested orgId
                certs.forEach(c -> {
                    Long certOrgId = c.getOrganization() != null ? c.getOrganization().getId() : null;
                    if (certOrgId == null || !certOrgId.equals(orgId)) {
                        log.warn("Certificate id={} appears to belong to different org (requested orgId={}, certOrgId={}). Certificate CN='{}', issuer='{}'",
                                c.getId(), orgId, certOrgId, c.getCommonName(), c.getIssuer() != null ? c.getIssuer().getCommonName() : null);
                    }
                });
            }

            return certs.stream()
                    .map(this::mapToDTO)
                    .toList();
        } catch (RuntimeException e) {
            log.error("getCertificatesByOrganization runtime error for email='{}': {}", email, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("getCertificatesByOrganization unexpected error for email='{}': {}", email, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch certificates by organization", e);
        }
    }

    public GetCertificateDTO mapToDTO(Certificate certificate) {
        boolean valid = certificateValidationService.validateCertificate(certificate);
        return new GetCertificateDTO(
                certificate.getId(),
                certificate.getCommonName(),
                certificate.getIssuer() != null ? certificate.getIssuer().getCommonName() : null,
                certificate.getType(),
                certificate.getValidFrom(),
                certificate.getValidTo(),
                certificate.getSerialNumber(),
                certificate.isRevoked(),
                valid,
                certificate.getPrivateKey() != null
        );
    }

    @Override
    public String getCertificatePemById(Long id) {
        Certificate cert = certificateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));
        return cert.getCertificatePem();
    }

    public GetCertificateDTO toDTO(Certificate c) {
        boolean valid = certificateValidationService.validateCertificate(c);

        return new GetCertificateDTO(
                c.getId(),
                c.getCommonName(),
                c.getIssuer() != null ? c.getIssuer().getCommonName() : null,
                c.getType(),
                c.getValidFrom(),
                c.getValidTo(),
                c.getSerialNumber(),
                c.isRevoked(),
                valid,
                c.getPrivateKey() != null
        );
    }
}
