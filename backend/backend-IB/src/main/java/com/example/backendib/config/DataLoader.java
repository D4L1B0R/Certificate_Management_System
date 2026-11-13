package com.example.backendib.config;

import com.example.backendib.models.entities.Organization;
import com.example.backendib.models.entities.User;
import com.example.backendib.models.enums.Role;
import com.example.backendib.repositories.OrganizationRepository;
import com.example.backendib.repositories.UserRepository;
import com.example.backendib.services.MasterKeyService;
import com.example.backendib.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final MasterKeyService masterKeyService;

    @Override
    public void run(String... args) {
        if (organizationRepository.existsByName("SYSTEM")) {
            System.out.println("DataLoader: SYSTEM org exists, skipping seeding.");
            return;
        }

        System.out.println("DataLoader: SYSTEM org missing, running initial seed.");

        Organization systemOrg = createOrganizationIfNotExists("SYSTEM");
        Organization org1 = createOrganizationIfNotExists("DefaultOrg");
        Organization org2 = createOrganizationIfNotExists("OtherOrg");

        createUserIfNotExists("admin@example.com", "Admin", "User", "Admin123*", org1, Role.ADMIN, true);
        createUserIfNotExists("ca@example.com", "Ca", "User", "Causer123*", org1, Role.CA, true);
        createUserIfNotExists("user@example.com", "Regular", "User", "User123*", org2, Role.USER, true);

        System.out.println("DataLoader: seeding complete.");
    }

    private Organization createOrganizationIfNotExists(String name) {
        return organizationRepository.findByName(name).orElseGet(() -> {
            try {
                Organization org = new Organization();
                org.setName(name);
                byte[] orgKey = EncryptionUtil.generateAesKey();
                var enc = masterKeyService.encryptWithPrimary(orgKey);
                org.setEncryptedOrgKey(enc.getKey());
                org.setEncryptedOrgKeyVersion(enc.getValue());
                Organization saved = organizationRepository.save(org);
                System.out.println("Created organization: " + name);
                return saved;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create organization " + name, e);
            }
        });
    }

    private void createUserIfNotExists(String email, String name, String surname, String rawPassword,
                                       Organization organization, Role role, boolean enabled) {
        if (userRepository.findByEmail(email).isEmpty()) {
            User user = new User();
            user.setEmail(email);
            user.setName(name);
            user.setSurname(surname);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setOrganization(organization);
            user.setRole(role);
            user.setEnabled(enabled);
            user.setActivationToken(null);
            userRepository.save(user);
            System.out.println("Created user: " + email + " with role " + role);
        }
    }
}