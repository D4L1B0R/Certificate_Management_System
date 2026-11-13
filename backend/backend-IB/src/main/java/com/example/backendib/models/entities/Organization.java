package com.example.backendib.models.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(name = "organizations")
@Entity
public class Organization {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private byte[] encryptedOrgKey;

    // version of the master key used to encrypt encryptedOrgKey
    // make nullable to avoid forcing a version when there is no key
    @Column(nullable = true)
    private Integer encryptedOrgKeyVersion;
}