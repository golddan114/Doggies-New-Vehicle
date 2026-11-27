package com.liucodex.doggiesnewvehicle.api.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Versioned, bounded and deterministic registry for optional combat command providers. */
public final class DnvCombatPolicyRegistry {
    public static final int DEFAULT_MAX_PROVIDERS = 8;
    private static final Pattern PROVIDER_ID = Pattern.compile(
            "[a-z][a-z0-9_.-]{1,63}");
    private static final DnvCombatPolicyRegistry GLOBAL =
            new DnvCombatPolicyRegistry(DEFAULT_MAX_PROVIDERS);

    private final int maxProviders;
    private final Map<String, DnvCombatPolicyProvider> providers = new TreeMap<>();
    private long revision;

    public DnvCombatPolicyRegistry(int maxProviders) {
        if (maxProviders < 1 || maxProviders > 32) {
            throw new IllegalArgumentException("maxProviders must be between 1 and 32");
        }
        this.maxProviders = maxProviders;
    }

    public static DnvCombatPolicyRegistry global() {
        return GLOBAL;
    }

    public static void registerProvider(DnvCombatPolicyProvider provider) {
        GLOBAL.register(provider);
    }

    public static boolean unregisterProvider(String providerId) {
        return GLOBAL.unregister(providerId);
    }

    public synchronized void register(DnvCombatPolicyProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String providerId = normalizeProviderId(provider.providerId());
        if (provider.contractVersion() != DnvCombatPolicyProvider.CONTRACT_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported combat provider contract " + provider.contractVersion());
        }
        if (providers.containsKey(providerId)) {
            throw new IllegalArgumentException("duplicate combat provider " + providerId);
        }
        if (providers.size() >= maxProviders) {
            throw new IllegalStateException("combat provider registry is full");
        }
        providers.put(providerId, provider);
        revision++;
    }

    public synchronized List<DnvCombatPolicyProvider> providers() {
        return List.copyOf(providers.values());
    }

    public synchronized boolean contains(String providerId) {
        return providers.containsKey(normalizeProviderId(providerId));
    }

    public synchronized boolean unregister(String providerId) {
        if (providers.remove(normalizeProviderId(providerId)) == null) {
            return false;
        }
        revision++;
        return true;
    }

    public synchronized RegistrySnapshot snapshot() {
        List<ProviderSnapshot> snapshots = new ArrayList<>(providers.size());
        providers.forEach((id, provider) -> snapshots.add(new ProviderSnapshot(
                id,
                provider.contractVersion())));
        return new RegistrySnapshot(revision, List.copyOf(snapshots));
    }

    private static String normalizeProviderId(String raw) {
        String normalized = Objects.requireNonNullElse(raw, "")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!PROVIDER_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid combat provider id " + raw);
        }
        return normalized;
    }

    public record RegistrySnapshot(long revision, List<ProviderSnapshot> providers) {
        public RegistrySnapshot {
            if (revision < 0L) {
                throw new IllegalArgumentException("revision must not be negative");
            }
            providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
        }
    }

    public record ProviderSnapshot(String providerId, int contractVersion) {
        public ProviderSnapshot {
            providerId = normalizeProviderId(providerId);
            if (contractVersion < 1) {
                throw new IllegalArgumentException("contractVersion must be positive");
            }
        }
    }
}
