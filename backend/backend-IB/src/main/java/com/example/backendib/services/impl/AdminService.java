package com.example.backendib.services.impl;

import com.example.backendib.dtos.caUser.CreateCaUserDTO;
import com.example.backendib.dtos.caUser.CreatedCaUserDTO;
import com.example.backendib.dtos.organization.CreateOrganizationDTO;
import com.example.backendib.dtos.organization.GetOrganizationDTO;
import com.example.backendib.dtos.user.GetUserDTO;
import com.example.backendib.models.entities.Organization;
import com.example.backendib.models.entities.User;
import com.example.backendib.models.enums.Role;
import com.example.backendib.repositories.OrganizationRepository;
import com.example.backendib.repositories.UserRepository;
import com.example.backendib.services.IAdminService;
import com.example.backendib.services.IEmailService;
import com.example.backendib.services.MasterKeyService;
import com.example.backendib.util.EncryptionUtil;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AdminService implements IAdminService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;
    private final IEmailService emailService;
    private final MasterKeyService masterKeyService;

    public AdminService(UserRepository userRepository, PasswordEncoder passwordEncoder, ModelMapper modelMapper, IEmailService emailService, OrganizationRepository organizationRepository, MasterKeyService masterKeyService) {
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.organizationRepository = organizationRepository;
        this.masterKeyService = masterKeyService;
    }


    @Override
    public CreatedCaUserDTO createCaUser(CreateCaUserDTO dto) {

        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("User with email already exists");
        }

        String token = UUID.randomUUID().toString();

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setSurname(dto.getSurname());

        Organization org = organizationRepository.findById(dto.getOrganizationId())
                .orElseThrow(() -> new RuntimeException("Org not found"));

        user.setOrganization(org);

        user.setRole(Role.CA);
        user.setEnabled(false);
        user.setActivationToken(token);
        user.setActivationUsed(false);
        user.setActivationExpiry(
                java.time.LocalDateTime.now().plusHours(24)
        );

        User saved = userRepository.save(user);

        emailService.sendActivationEmail(saved.getEmail(), token, "password");

        return modelMapper.map(saved, CreatedCaUserDTO.class);
    }

    @Override
    public GetOrganizationDTO createOrganization(CreateOrganizationDTO dto) {

        if (organizationRepository.existsByName(dto.getName())) {
            throw new RuntimeException("Organization already exists");
        }

        Organization org = new Organization();
        org.setName(dto.getName());

        try {
            byte[] orgKey = EncryptionUtil.generateAesKey();
            // encrypt with current primary master key
            var enc = masterKeyService.encryptWithPrimary(orgKey);
            org.setEncryptedOrgKey(enc.getKey());
            org.setEncryptedOrgKeyVersion(enc.getValue());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate org key", e);
        }

        Organization saved = organizationRepository.save(org);

        return new GetOrganizationDTO(saved.getId(), saved.getName());
    }

    @Override
    public List<GetUserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(u -> new GetUserDTO(
                        u.getId(),
                        u.getEmail(),
                        u.getName(),
                        u.getSurname(),
                        u.getRole().name()
                ))
                .toList();
    }

    @Override
    public List<GetUserDTO> getUsersByRole(Role role) {
        return userRepository.findByRole(role)
                .stream()
                .map(u -> new GetUserDTO(
                        u.getId(),
                        u.getEmail(),
                        u.getName(),
                        u.getSurname(),
                        u.getRole().name()
                ))
                .toList();
    }

    @Override
    public List<GetOrganizationDTO> getAllOrganizations() {
        return organizationRepository.findAll()
                .stream()
                .map(o -> new GetOrganizationDTO(o.getId(), o.getName()))
                .toList();
    }
}
