package com.example.backendib.models.entities;

import com.example.backendib.models.enums.CertificateType;
import com.example.backendib.models.enums.RevocationReason;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "certificates")
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String commonName;

    @Column(columnDefinition = "TEXT")
    private String certificatePem;

    private byte[] privateKey;

    @Enumerated(EnumType.STRING)
    private CertificateType type;

    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String serialNumber;

    private boolean revoked;
    private LocalDateTime revocationDate;
    @Enumerated(EnumType.STRING)
    private RevocationReason revocationReason;

    @ManyToOne
    private Certificate issuer;

    @ManyToOne
    private Organization organization;

    @ManyToOne
    private User owner;
}