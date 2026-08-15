package pl.corekit.feature.home;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import pl.corekit.CoreKitPlugin;
import pl.corekit.storage.HomeRepository;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates home persistence with an in-memory cache of each online player's
 * home names.
 *
 * <p>The cache exists solely so tab-completion is instant and free of database
 * round-trips on every keystroke. It is authoritative only for suggestions; all
 * reads/writes still go through {@link HomeRepository}, and the cache is updated
 * to match after each successful mutation.
 */
public final class HomeService {

    private static final String LIMIT_PREFIX = "corekit.homes.limit.";
    private static final String UNLIMITED_PERMISSION = "corekit.homes.unlimited";

    private final CoreKitPlugin plugin;
    private final HomeRepository repository;

    /** UUID → set of (lower-cased) home names, for tab-completion. */
    private final Map<UUID, Set<String>> nameCache = new ConcurrentHashMap<>();

    public HomeService(CoreKitPlugin plugin, HomeRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public HomeRepository repository() {
        return repository;
    }

    /** Loads a player's home names into the cache (called on join). */
    public void warmCache(Player player) {
        UUID uuid = player.getUniqueId();
        repository.findAll(uuid).thenAccept(homes -> {
            Set<String> names = ConcurrentHashMap.newKeySet();
            homes.forEach(home -> names.add(home.name()));
            nameCache.put(uuid, names);
        }).exceptionally(throwable -> {
            plugin.getSLF4JLogger().warn("Failed to warm home cache for {}",
                    player.getName(), throwable);
            return null;
        });
    }

    public void evictCache(UUID uuid) {
        nameCache.remove(uuid);
    }

    public Set<String> cachedNames(UUID uuid) {
        return nameCache.getOrDefault(uuid, Collections.emptySet());
    }

    public void rememberName(UUID uuid, String name) {
        nameCache.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet())
                .add(name.toLowerCase(Locale.ROOT));
    }

    public void forgetName(UUID uuid, String name) {
        Set<String> names = nameCache.get(uuid);
        if (names != null) {
            names.remove(name.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Resolves a player's maximum home count from permissions.
     *
     * <p>Priority: {@code corekit.homes.unlimited} → highest numeric
     * {@code corekit.homes.limit.<n>} → the configured default. Reading the
     * highest granted numeric node (rather than the first) means overlapping
     * rank permissions compose sensibly.
     */
    public int homeLimit(Player player) {
        if (player.hasPermission(UNLIMITED_PERMISSION)) {
            return Integer.MAX_VALUE;
        }
        int limit = plugin.configManager().settings().defaultHomeLimit();
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission().toLowerCase(Locale.ROOT);
            if (permission.startsWith(LIMIT_PREFIX)) {
                try {
                    limit = Math.max(limit, Integer.parseInt(permission.substring(LIMIT_PREFIX.length())));
                } catch (NumberFormatException ignored) {
                    // Non-numeric suffix — not a real limit node, skip it.
                }
            }
        }
        return limit;
    }
}
