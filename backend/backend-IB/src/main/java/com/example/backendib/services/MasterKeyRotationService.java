package com.example.backendib.services;

import com.example.backendib.models.entities.Organization;
import com.example.backendib.repositories.OrganizationRepository;
import com.example.backendib.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MasterKeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(MasterKeyRotationService.class);

    private final MasterKeyService masterKeyService;
    private final OrganizationRepository organizationRepository;
    private final PlatformTransactionManager txManager;

    // configurable rotation interval and batch size (defaults)
    @Value("${app.master-key-rotation-interval-days:180}")
    private long rotationIntervalDays;

    @Value("${app.master-key-rotation-batch-size:50}")
    private int defaultBatchSize;

    // single-threaded scheduler for rotation tasks (keeps system responsive)
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "master-key-rotation");
        t.setDaemon(true);
        return t;
    });

    // store failed org ids from last rotation attempt for admin visibility
    private final List<Long> lastFailedOrgIds = new ArrayList<>();

    @PostConstruct
    public void startAutoRotation() {
        // short fixed initial delay to allow other @PostConstruct to run
        long initialDelaySeconds = 60L;
        long periodSeconds = Duration.ofDays(rotationIntervalDays).getSeconds();
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                // guard: if master keys not yet initialized, skip this run
                if (masterKeyService.getMasterKeys() == null || masterKeyService.getMasterKeys().isEmpty()) {
                    log.debug("Master keys not initialized yet - skipping scheduled rotation");
                    return;
                }
                log.info("Automatic master key rotation triggered (interval {} days)", rotationIntervalDays);
                byte[] newKey = com.example.backendib.util.EncryptionUtil.generateAesKey();
                // call rotateMasterKey with configured batch size; swallow exceptions (logged)
                rotateMasterKey(newKey, defaultBatchSize, false);
            } catch (Exception e) {
                log.error("Automatic master key rotation failed", e);
            }
        }, initialDelaySeconds, Math.max(60L, periodSeconds), TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdownScheduler() {
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    // new check placed at start of rotation to avoid creating new primary when historical keys are missing
    private void assertNoMissingHistoricalKeysUnlessForced(boolean force) {
        // collect referenced versions from all organizations
        Set<Integer> referenced = organizationRepository.findAll()
                .stream()
                .map(Organization::getEncryptedOrgKeyVersion)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Integer> known = masterKeyService.getMasterKeys().keySet();

        // determine missing versions
        Set<Integer> missing = referenced.stream()
                .filter(v -> !known.contains(v))
                .collect(Collectors.toSet());

        if (!missing.isEmpty() && !masterKeyService.isVaultWrapKeyAvailable() && !force) {
            String msg = "Rotation blocked: missing master key versions referenced by organizations: " + missing +
                    ". Persist or import those historical master keys (use admin import) or provide vault wrap key (env MASTER_KEY or app.vault-wrap-key).";
            log.error(msg);
            throw new RuntimeException(msg);
        }
    }

    // keep compatibility: existing method delegates to new implementation (non-forced)
    public synchronized int rotateMasterKey(byte[] newMasterKeyBytes, int batchSize) throws Exception {
        return rotateMasterKey(newMasterKeyBytes, batchSize, false);
    }

    // new overload supporting "force" behaviour
    public synchronized int rotateMasterKey(byte[] newMasterKeyBytes, int batchSize, boolean force) throws Exception {
        log.info("Starting master key rotation (batchSize={}, force={})", batchSize, force);
        lastFailedOrgIds.clear();

        // defensive pre-check: if some orgs reference unknown historical versions and no vault wrap key available,
        // abort rotation unless admin explicitly forces it.
        assertNoMissingHistoricalKeysUnlessForced(force);

        int newVersion = masterKeyService.nextVersion();
        // add new key but don't make primary until re-encryption completes (unless forced at end)
        masterKeyService.addMasterKey(newVersion, newMasterKeyBytes, false);

        Long lastId = 0L;
        boolean more = true;
        boolean hadFailures = false;

        while (more) {

            List<Organization> orgs =
                    organizationRepository.findTop50ByIdGreaterThanOrderByIdAsc(lastId);

            if (orgs.isEmpty()) {
                break;
            }

            for (Organization org : orgs) {
                lastId = org.getId();

                if (org.getEncryptedOrgKey() == null) continue;

                try {
                    Map.Entry<byte[], Integer> dec = masterKeyService.decryptOrgKey(org);
                    byte[] orgKeyPlain = dec.getKey();

                    byte[] newMaster = masterKeyService.getMasterKeys().get(newVersion);
                    byte[] newEncrypted = EncryptionUtil.encrypt(orgKeyPlain, newMaster);

                    org.setEncryptedOrgKey(newEncrypted);
                    org.setEncryptedOrgKeyVersion(newVersion);
                    organizationRepository.save(org);

                } catch (Exception e) {
                    hadFailures = true;
                    lastFailedOrgIds.add(org.getId());
                }
            }
        }

        if (hadFailures) {
            String msg = "Rotation incomplete: some orgs failed to re-encrypt. Check logs for failed org ids.";
            log.error("Rotation completed but some orgs failed to re-encrypt. New master key version {} added but not set as primary. Failed orgs: {}", newVersion, lastFailedOrgIds);
            if (!force) {
                // keep previous behavior: do not flip primary and indicate failure
                throw new RuntimeException(msg);
            } else {
                // force behavior: set primary despite failures (admin opted in)
                masterKeyService.setPrimaryVersion(newVersion);
                log.warn("Force-rotation: new primary set to {} despite {} failed orgs", newVersion, lastFailedOrgIds.size());
                return newVersion;
            }
        }

        // after successful re-encryption of all orgs, make new master key primary
        masterKeyService.setPrimaryVersion(newVersion);
        log.info("Master key rotation complete. New primary version = {}", newVersion);
        return newVersion;
    }

    // admin helper: scan all orgs and return problems (orgId, storedVersion, error)
    public List<Map<String, Object>> findDecryptionProblems() {
        List<Map<String, Object>> problems = new ArrayList<>();
        Iterable<Organization> all = organizationRepository.findAll();
        for (Organization org : all) {
            if (org.getEncryptedOrgKey() == null) continue;
            try {
                masterKeyService.decryptOrgKey(org);
            } catch (Exception e) {
                Map<String, Object> m = new HashMap<>();
                m.put("orgId", org.getId());
                m.put("storedVersion", org.getEncryptedOrgKeyVersion());
                m.put("error", e.getMessage());
                problems.add(m);
            }
        }
        return problems;
    }

    public List<Long> getLastFailedOrgIds() {
        return List.copyOf(lastFailedOrgIds);
    }
}
