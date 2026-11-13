package com.example.backendib.services;

import com.example.backendib.util.EncryptionUtil;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

import java.util.Base64;
import java.time.Instant;

import com.example.backendib.services.vault.LocalVaultService;
import com.example.backendib.services.vault.LocalVaultService.VaultEntry;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

@Service
public class MasterKeyService {

    private static final Logger log = LoggerFactory.getLogger(MasterKeyService.class);

    // primary master key base64 (optional fallback)
    @Value("${app.master-key:}")
    private String primaryMasterKeyBase64;

    // optional wrap key (base64) for vault encryption; fallback to env MASTER_KEY if not set
    @Value("${app.vault-wrap-key:}")
    private String vaultWrapKeyBase64;

    // vault file path (optional override)
    @Value("${app.vault-file:./vault.dat}")
    private String vaultFilePath;

    private final LocalVaultService localVaultService;

    public MasterKeyService(LocalVaultService localVaultService) {
        this.localVaultService = localVaultService;
    }

    @Getter
    private final NavigableMap<Integer, byte[]> masterKeys = new TreeMap<>();

    @Getter
    private int primaryVersion = 1;

    // expected normalized key length (set from primary at init)
    private int expectedKeyLength = 0;

    @PostConstruct
    public void init() {
        try {
            // determine wrap key bytes (for vault encryption)
            // prefer explicit vault wrap config, then env MASTER_KEY, then fallback to app.master-key
            String wrapB64 = firstNonBlank(vaultWrapKeyBase64, System.getenv("MASTER_KEY"), primaryMasterKeyBase64);

            byte[] wrapKeyBytes = null;
            if (wrapB64 != null && !wrapB64.isBlank()) {
                try {
                    wrapKeyBytes = Base64.getDecoder().decode(wrapB64);
                } catch (IllegalArgumentException iae) {
                    log.warn("Configured wrap key is not valid base64 - ignoring wrap persistence: {}", iae.getMessage());
                    wrapKeyBytes = null;
                }
            }

            // If vault file exists and wrap key available, load persisted vault (file authoritative)
            if (wrapKeyBytes != null && localVaultService.exists(vaultFilePath)) {
                try {
                    List<VaultEntry> entries = localVaultService.load(vaultFilePath, wrapKeyBytes);
                    if (entries != null && !entries.isEmpty()) {
                        masterKeys.clear();
                        for (VaultEntry e : entries) {
                            byte[] key = Base64.getDecoder().decode(e.getKeyBase64());
                            // normalize if necessary
                            if (key.length != 16 && key.length != 24 && key.length != 32) {
                                key = normalizeKeyLength(key, 32);
                                log.debug("Normalized persisted master key version {} to length {}", e.getVersion(), key.length);
                            }
                            masterKeys.put(e.getVersion(), key);
                            if (Boolean.TRUE.equals(e.getPrimary())) {
                                primaryVersion = e.getVersion();
                            }
                        }
                        if (masterKeys.isEmpty()) throw new RuntimeException("Vault loaded but contained no keys");
                        expectedKeyLength = masterKeys.get(primaryVersion).length;
                        log.info("MasterKeyService loaded {} keys from vault, primaryVersion={}, keyLength={}", masterKeys.size(), primaryVersion, expectedKeyLength);
                        return;
                    }
                } catch (Exception ex) {
                    log.warn("Failed to load vault from {}: {}. Will attempt fallback init", vaultFilePath, ex.getMessage());
                    // continue to fallback behavior
                }
            }

            // fallback: try configured primary master key (app.master-key or env MASTER_KEY)
            String primaryB64 = firstNonBlank(primaryMasterKeyBase64, System.getenv("MASTER_KEY"));

            if (primaryB64 == null || primaryB64.isBlank()) {
                throw new RuntimeException("No master keys found in vault and no app.master-key/MASTER_KEY configured");
            }

            byte[] primary = Base64.getDecoder().decode(primaryB64);
            if (primary.length != 16 && primary.length != 24 && primary.length != 32) {
                log.warn("Primary master key length {} is not standard AES length; normalizing to 32 bytes", primary.length);
                primary = normalizeKeyLength(primary, 32);
            }

            masterKeys.put(1, primary);
            primaryVersion = 1;
            expectedKeyLength = primary.length;
            log.info("MasterKeyService initialized from config: primaryVersion={}, keyLength={}", primaryVersion, expectedKeyLength);

            // persist initial master key into vault if wrap key available (use same fallback wrap key)
            if (wrapKeyBytes != null) {
                try {
                    VaultEntry ve = new VaultEntry(1, Base64.getEncoder().encodeToString(primary), true, Instant.now().toEpochMilli());
                    localVaultService.store(List.of(ve), vaultFilePath, wrapKeyBytes);
                    log.info("Persisted initial master key into local vault at {}", vaultFilePath);
                } catch (Exception ex) {
                    log.warn("Failed to persist initial master key into vault: {}", ex.getMessage());
                }
            } else {
                log.warn("Vault wrap key not available; master key will live in-memory only");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize master key", e);
        }
    }

    // helper to return first non-blank string from list
    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String s : vals) {
            if (s != null && !s.isBlank()) return s;
        }
        return null;
    }

    public synchronized void addMasterKey(int version, byte[] keyBytes, boolean makePrimary) {
        // normalize key length to expectedKeyLength if known
        byte[] toStore = keyBytes;
        if (expectedKeyLength > 0 && keyBytes != null && keyBytes.length != expectedKeyLength) {
            toStore = normalizeKeyLength(keyBytes, expectedKeyLength);
            log.debug("Normalized master key for version {} from {} to {}", version, keyBytes.length, toStore.length);
        } else if (expectedKeyLength == 0 && keyBytes != null) {
            // first time unknown -> adopt provided length (ensure acceptable AES length)
            if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                toStore = normalizeKeyLength(keyBytes, 32);
            }
            expectedKeyLength = toStore.length;
        }
        masterKeys.put(version, toStore);
        if (makePrimary) primaryVersion = version;
        log.info("Added master key version {} (primary={} )", version, makePrimary);

        // persist into local vault: use vaultWrapKeyBase64 || env MASTER_KEY || app.master-key (fallback)
        try {
            String wrapB64 = firstNonBlank(vaultWrapKeyBase64, System.getenv("MASTER_KEY"), primaryMasterKeyBase64);
            if (wrapB64 != null && !wrapB64.isBlank()) {
                byte[] wrapKeyBytes = Base64.getDecoder().decode(wrapB64);
                List<VaultEntry> entries = masterKeys.entrySet().stream()
                        .map(e -> new VaultEntry(e.getKey(), Base64.getEncoder().encodeToString(e.getValue()), e.getKey().equals(primaryVersion), Instant.now().toEpochMilli()))
                        .collect(Collectors.toList());
                localVaultService.store(entries, vaultFilePath, wrapKeyBytes);
                log.debug("Persisted master keys to vault after addMasterKey");
            } else {
                log.warn("Vault wrap key not available; skipping persisting master key to vault");
            }
        } catch (Exception ex) {
            log.error("Failed to persist master key to vault: {}", ex.getMessage());
        }
    }

    public synchronized void setPrimaryVersion(int version) {
        if (!masterKeys.containsKey(version)) {
            throw new RuntimeException("Cannot set primary to unknown master key version " + version);
        }
        this.primaryVersion = version;
        log.info("Primary master version set to {}", version);

        // persist primary flag change with same fallback
        try {
            String wrapB64 = firstNonBlank(vaultWrapKeyBase64, System.getenv("MASTER_KEY"), primaryMasterKeyBase64);
            if (wrapB64 != null && !wrapB64.isBlank()) {
                byte[] wrapKeyBytes = Base64.getDecoder().decode(wrapB64);
                List<VaultEntry> entries = masterKeys.entrySet().stream()
                        .map(e -> new VaultEntry(e.getKey(), Base64.getEncoder().encodeToString(e.getValue()), e.getKey().equals(primaryVersion), Instant.now().toEpochMilli()))
                        .collect(Collectors.toList());
                localVaultService.store(entries, vaultFilePath, wrapKeyBytes);
                log.debug("Persisted master keys to vault after primary switch");
            } else {
                log.warn("Vault wrap key not available; skipping persisting primary switch to vault");
            }
        } catch (Exception ex) {
            log.error("Failed to persist primary switch to vault: {}", ex.getMessage());
        }
    }

    public synchronized int nextVersion() {
        if (masterKeys.isEmpty()) return 1;
        return masterKeys.lastKey() + 1;
    }

    public Map.Entry<byte[], Integer> decryptWithAnyMasterKey(byte[] ciphertext) throws Exception {
        if (ciphertext == null || ciphertext.length < 13) {
            throw new RuntimeException("Invalid ciphertext (too short) for decryption");
        }
        // try newer keys first (descending versions) as they are more likely to succeed
        NavigableMap<Integer, byte[]> descending = masterKeys.descendingMap();
        for (Map.Entry<Integer, byte[]> e : descending.entrySet()) {
            Integer ver = e.getKey();
            byte[] key = e.getValue();
            try {
                byte[] plaintext = EncryptionUtil.decrypt(ciphertext, key);
                log.debug("Decryption succeeded with master version {}", ver);
                return new AbstractMap.SimpleEntry<>(plaintext, ver);
            } catch (Exception ex) {
                // AEADBadTag and others will be tried with next key
                log.debug("Decryption with master version {} failed: {}", ver, ex.getClass().getSimpleName());
            }
        }
        // build a helpful error message listing known versions
        StringBuilder sb = new StringBuilder();
        sb.append("Unable to decrypt with known master keys. Known versions: ");
        sb.append(masterKeys.keySet().toString());
        throw new RuntimeException(sb.toString());
    }

    public Map.Entry<byte[], Integer> decryptOrgKey(com.example.backendib.models.entities.Organization org) throws Exception {
        if (org.getEncryptedOrgKey() == null) throw new RuntimeException("Org has no encrypted key");
        // try decrypt using stored version first if present
        Integer ver = org.getEncryptedOrgKeyVersion();
        if (ver != null) {
            if (masterKeys.containsKey(ver)) {
                try {
                    byte[] plaintext = EncryptionUtil.decrypt(org.getEncryptedOrgKey(), masterKeys.get(ver));
                    log.debug("Decrypted org key using stored version {}", ver);
                    return new AbstractMap.SimpleEntry<>(plaintext, ver);
                } catch (Exception ignored) {
                    log.debug("Stored-version decrypt failed for org {} with version {} - will try other keys", org.getId(), ver);
                    // fallthrough to try any key
                }
            } else {
                log.warn("Org {} references master key version {} which is not present in memory. Known versions: {}",
                        org.getId(), ver, masterKeys.keySet());
                // fallthrough to try other keys (best-effort)
            }
        }
        // fallback: try any key (newer versions first)
        return decryptWithAnyMasterKey(org.getEncryptedOrgKey());
    }

    public Map.Entry<byte[], Integer> encryptWithPrimary(byte[] data) throws Exception {
        byte[] key = masterKeys.get(primaryVersion);
        if (key == null) throw new RuntimeException("Primary master key not found");
        byte[] encrypted = EncryptionUtil.encrypt(data, key);
        return new AbstractMap.SimpleEntry<>(encrypted, primaryVersion);
    }

    // helper: derive/normalize key bytes to desired length via SHA-256 truncation
    private byte[] normalizeKeyLength(byte[] inputKey, int desiredLen) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    Base64.getEncoder().encodeToString(inputKey).toCharArray(),
                    "master-key-salt".getBytes(),
                    10000,
                    desiredLen * 8
            );

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] key = factory.generateSecret(spec).getEncoded();

            return Arrays.copyOf(key, desiredLen);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // helper: check if a vault wrap key is configured/present (env or property)
    public boolean isVaultWrapKeyAvailable() {
        try {
            if (vaultWrapKeyBase64 != null && !vaultWrapKeyBase64.isBlank()) return true;
            String env = System.getenv("MASTER_KEY");
            return env != null && !env.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    // helper: return wrap key bytes if available, otherwise null
    public byte[] getVaultWrapKeyBytes() {
        try {
            String wrapB64 = vaultWrapKeyBase64;
            if (wrapB64 == null || wrapB64.isBlank()) {
                String env = System.getenv("MASTER_KEY");
                if (env != null && !env.isBlank()) wrapB64 = env;
            }
            if (wrapB64 == null || wrapB64.isBlank()) return null;
            return Base64.getDecoder().decode(wrapB64);
        } catch (Exception e) {
            log.warn("Failed to decode vault wrap key bytes: {}", e.getMessage());
            return null;
        }
    }
}
