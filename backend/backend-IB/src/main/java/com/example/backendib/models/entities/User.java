package com.example.backendib.models.entities;

import com.example.backendib.models.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter

@Table(name = "users")
@Entity
public class User {
    @Id
    @GeneratedValue
    private Long id;

    private String email;
    private String password;

    private String name;
    private String surname;

    @ManyToOne
    private Organization organization;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean enabled;

    @Column(length = 100)
    private String activationToken;

    @Column(columnDefinition = "TEXT")
    private String publicKey;
    private LocalDateTime activationExpiry;
    private boolean activationUsed;

    @Column(length = 2000)
    private String activationChallenge;

    @Column(columnDefinition = "TEXT")
    private String encryptedActivationChallenge;

    private String recoveryChallenge;
    private boolean recoveryUsed = false;
    private LocalDateTime recoveryExpiry;

    @Column(columnDefinition = "TEXT")
    private String encryptedRecoveryChallenge;

    private String totpSecret;

    private boolean totpEnabled;
}
