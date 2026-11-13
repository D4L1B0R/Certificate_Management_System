package com.example.backendib.controllers;

import com.example.backendib.dtos.caUser.CreateCaUserDTO;
import com.example.backendib.dtos.caUser.CreatedCaUserDTO;
import com.example.backendib.dtos.organization.CreateOrganizationDTO;
import com.example.backendib.dtos.organization.GetOrganizationDTO;
import com.example.backendib.dtos.user.GetUserDTO;
import com.example.backendib.models.enums.Role;
import com.example.backendib.services.IAdminService;
import com.example.backendib.services.MasterKeyRotationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final IAdminService adminService;
    private final MasterKeyRotationService rotationService;
    private final com.example.backendib.services.MasterKeyService masterKeyService;

    public AdminController(IAdminService adminService, MasterKeyRotationService rotationService,
                           com.example.backendib.services.MasterKeyService masterKeyService) {
        this.adminService = adminService;
        this.rotationService = rotationService;
        this.masterKeyService = masterKeyService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/ca-users")
    public ResponseEntity<CreatedCaUserDTO> createCaUser(@Valid @RequestBody CreateCaUserDTO createCaUserDTO) {

        CreatedCaUserDTO response = adminService.createCaUser(createCaUserDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/organizations")
    public ResponseEntity<GetOrganizationDTO> createOrg(@RequestBody CreateOrganizationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createOrganization(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public ResponseEntity<List<GetUserDTO>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ca-users")
    public ResponseEntity<List<GetUserDTO>> getCaUsers() {
        return ResponseEntity.ok(adminService.getUsersByRole(Role.CA));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/organizations")
    public ResponseEntity<List<GetOrganizationDTO>> getAllOrganizations() {
        return ResponseEntity.ok(adminService.getAllOrganizations());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/rotate-master")
    public ResponseEntity<Map<String, Object>> rotateMaster(@RequestBody(required = false) Map<String, Object> body) {
        try {
            int batchSize = body != null && body.get("batchSize") instanceof Number
                    ? ((Number) body.get("batchSize")).intValue()
                    : Integer.parseInt(System.getProperty("app.master-key-rotation-batch-size", "50"));

            boolean force = body != null && Boolean.TRUE.equals(body.get("force"));

            byte[] newKey;
            if (body != null && body.get("masterKeyBase64") instanceof String) {
                String b64 = (String) body.get("masterKeyBase64");
                newKey = Base64.getDecoder().decode(b64);
            } else {
                newKey = com.example.backendib.util.EncryptionUtil.generateAesKey();
            }

            int newVersion = rotationService.rotateMasterKey(newKey, batchSize, force);
            // include failed org ids if any
            List<Long> failed = rotationService.getLastFailedOrgIds();

            return ResponseEntity.ok(Map.of(
                    "newVersion", newVersion,
                    "failedOrgIds", failed
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Rotation failed",
                    "message", e.getMessage(),
                    "failedOrgIds", rotationService.getLastFailedOrgIds()
            ));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/rotation-status")
    public ResponseEntity<Map<String, Object>> rotationStatus() {
        int primary = masterKeyService.getPrimaryVersion();
        int known = masterKeyService.getMasterKeys().size();
        return ResponseEntity.ok(Map.of(
                "primaryVersion", primary,
                "knownMasterKeys", known
        ));
    }

    // new endpoint to list orgs that cannot be decrypted with current master keys
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/rotation-problems")
    public ResponseEntity<List<Map<String, Object>>> rotationProblems() {
        List<Map<String, Object>> problems = rotationService.findDecryptionProblems();
        return ResponseEntity.ok(problems);
    }

    @GetMapping("/debug")
    public String debug(Authentication auth) {
        return auth.getAuthorities().toString();
    }

}
