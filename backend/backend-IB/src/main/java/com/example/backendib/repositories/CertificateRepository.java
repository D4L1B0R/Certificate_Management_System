package com.example.backendib.repositories;

import com.example.backendib.models.entities.Certificate;
import com.example.backendib.models.entities.User;
import com.example.backendib.models.enums.CertificateType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    List<Certificate> findByOwnerAndType(User owner, CertificateType type);
    Optional<Certificate> findBySerialNumber(String serialNumber);
    List<Certificate> findAllByOrganizationId(Long orgId);
    List<Certificate> findByRevokedFalse();
}
