package com.example.backendib.controllers;

import com.example.backendib.dtos.certificate.*;
import com.example.backendib.models.entities.Certificate;
import com.example.backendib.models.enums.CertificateType;
import com.example.backendib.services.*;
import com.example.backendib.services.impl.CertificateCrlService;
import com.example.backendib.util.AuthUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.openssl.PEMParser;
import java.io.StringReader;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import com.example.backendib.models.entities.User;
import com.example.backendib.repositories.UserRepository;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final ICertificateIssuanceService issuanceService;
    private final ICertificateQueryService queryService;
    private final ICertificateValidationService validationService;
    private final IKeystoreService keystoreService;
    private final CertificateCrlService crlService;
    private final ICertificateRevocationService revocationService;

    private final UserRepository userRepository;


    @PostMapping("/root")
    public Certificate generateRoot(@RequestBody RootCertificateRequestDTO req) {

        return issuanceService.generateRootCertificate(
                req.commonName,
                req.organizationalUnit,
                req.country,
                req.organizationId,
                req.validFrom,
                req.validTo
        );
    }

    @GetMapping("/validate/{id}")
    public ResponseEntity<Boolean> validateById(@PathVariable Long id) {
        return ResponseEntity.ok(validationService.validateById(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','CA')")
    @PostMapping("/issue-intermediate")
    public ResponseEntity<GetCertificateDTO> issueIntermediate(
            @Valid @RequestBody CreateCertificateDTO request
    ) {
        return ResponseEntity.ok(issuanceService.issueIntermediateCertificate(request));
    }

    @PreAuthorize("hasAnyRole('ADMIN','CA','USER')")
    @GetMapping("/issuers")
    public ResponseEntity<List<GetCertificateDTO>> getAllValidIssuers(
            @RequestParam CertificateType targetType
    ) {
        String email = AuthUtils.getCurrentUserEmail();
        User u = userRepository.findByEmail(email).orElse(null);

        ResponseEntity.BodyBuilder rb = ResponseEntity.ok();
        if (u != null && u.getOrganization() != null) {
            rb.header("X-ORG-ID", String.valueOf(u.getOrganization().getId()));
            rb.header("X-ORG-NAME", u.getOrganization().getName());
        }

        return rb.body(queryService.getAllValidIssuers(targetType));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<GetCertificateDTO>> getAll() {
        return ResponseEntity.ok(queryService.getAllCertificates());
    }

    @PreAuthorize("hasRole('CA')")
    @GetMapping("/organization")
    public ResponseEntity<List<GetCertificateDTO>> getOrgCertificates() {
        String email = AuthUtils.getCurrentUserEmail();
        try {
            return ResponseEntity.ok(queryService.getCertificatesByOrganization(email));
        } catch (org.springframework.dao.IncorrectResultSizeDataAccessException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @PreAuthorize("hasAnyRole('USER', 'CA')")
    @GetMapping("/my")
    public ResponseEntity<List<GetCertificateDTO>> getMyCertificates() {
        String email = AuthUtils.getCurrentUserEmail();
        return ResponseEntity.ok(queryService.getMyCertificates(email));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CertificateDetailsDTO> getCertificateDetails(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getCertificateDetails(id));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/issue/end-entity/autogenerate", produces = "application/x-pkcs12")
    public ResponseEntity<byte[]> issueEeAutogenerate(
            @Valid @RequestBody CreateEeCertificateDTO request
    ) {
        byte[] keystore = issuanceService.issueEndEntityAutogenerate(request);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=certificate.p12")
                .body(keystore);
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/issue-csr")
    public ResponseEntity<GetCertificateDTO> issueCertificateFromCsr(
            @Valid @RequestBody CreateCsrCertificateDTO request) {
        return ResponseEntity.ok(issuanceService.issueCertificateFromCsr(request));
    }

    @GetMapping(value = "/download/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','CA','USER')")
    public ResponseEntity<byte[]> downloadCertificateCER(@PathVariable Long id) {
        try {
            String pem = queryService.getCertificatePemById(id);
            // Convert PEM to DER (CER is DER in binary)
            byte[] der = keystoreService.pemToDer(pem);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=certificate_" + id + ".cer")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(der);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/download-pem/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','CA','USER')")
    public ResponseEntity<byte[]> downloadCertificatePEM(@PathVariable Long id) {
        try {
            String pem = queryService.getCertificatePemById(id);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=certificate_" + id + ".pem")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(pem.getBytes());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN','CA','USER')")
    @GetMapping(value = "/download-keystore/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> downloadPkcs12(
            @PathVariable Long id,
            @RequestParam(defaultValue = "changeit") String password
    ) {
        try {
            byte[] p12 = keystoreService.getPkcs12ById(id, password);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=certificate_" + id + ".p12")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(p12);
        } catch (IllegalStateException e) {
            // e.g. no private key present
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/crl")
    public ResponseEntity<List<CrlEntryDTO>> getCRL() {
        return ResponseEntity.ok(crlService.getCRL());
    }

    @PostMapping("/revoke/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CA', 'USER')")
    public ResponseEntity<Void> revokeCertificate(
            @PathVariable Long id,
            @RequestBody RevokeRequestDTO request
    ) {
        revocationService.revokeCertificate(id, request.getReason());
        return ResponseEntity.ok().build();
    }
}
