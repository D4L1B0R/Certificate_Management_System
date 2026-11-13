package com.example.backendib.repositories;

import com.example.backendib.models.entities.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByName(String name);
    boolean existsByName(String name);
    List<Organization> findTop50ByIdGreaterThanOrderByIdAsc(Long id);
}
