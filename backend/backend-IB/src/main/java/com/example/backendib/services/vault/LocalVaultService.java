package com.example.backendib.services.vault;

import com.example.backendib.util.EncryptionUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

// lightweight local vault: persist encrypted JSON blob to file
@Service
@RequiredArgsConstructor
public class LocalVaultService {

    private static final Logger log = LoggerFactory.getLogger(LocalVaultService.class);

    private final ObjectMapper objectMapper;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VaultEntry {
        private Integer version;
        private String keyBase64; // plaintext master key encoded as base64 (the JSON blob itself is encrypted)
        private Boolean primary;
        private Long createdAt;
    }

    public boolean exists(String path) {
        try {
            File f = new File(path);
            return f.exists() && f.isFile();
        } catch (Exception e) {
            return false;
        }
    }

    // load and decrypt vault file, return list of entries (keyBase64 contains plaintext master-key base64)
    public List<VaultEntry> load(String path, byte[] wrapKey) throws Exception {
        File f = new File(path);
        if (!f.exists() || f.length() == 0) return new ArrayList<>();

        byte[] encrypted = Files.readAllBytes(f.toPath());
        byte[] decrypted = EncryptionUtil.decrypt(encrypted, wrapKey);

        if (decrypted == null || decrypted.length == 0) {
            return new ArrayList<>();
        }

        // use explicit TypeReference for List<VaultEntry>
        return objectMapper.readValue(decrypted, new TypeReference<List<VaultEntry>>() {});
    }

    // store list of entries: serialize to JSON, encrypt with wrapKey, write atomically to file
    public void store(List<VaultEntry> entries, String path, byte[] wrapKey) throws Exception {
        byte[] jsonBytes = objectMapper.writeValueAsBytes(entries);
        byte[] encrypted = EncryptionUtil.encrypt(jsonBytes, wrapKey);

        Path target = Path.of(path);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // write to temp file then move atomically (best-effort)
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try {
            Files.write(tmp, encrypted);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.debug("Vault successfully written to {}", target);
        } catch (Exception ex) {
            log.error("Failed to persist vault to {}: {}", path, ex.getMessage());
            // ensure tmp removed
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
            throw ex;
        }
    }
}
