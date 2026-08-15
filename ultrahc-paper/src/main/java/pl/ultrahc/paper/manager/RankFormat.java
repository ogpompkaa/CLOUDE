package pl.ultrahc.paper.manager;

import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.common.storage.Storage;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wspolne budowanie prefiksu rangi gracza (poziom/gwiazdka + ewentualne podium
 * #1/#2/#3 UHC). Uzywane przez TAB/nametag (RankService) oraz format czatu.
 * Dziala w obu rolach; podium tylko gdy dostepne topki (lobby).
 */
public class RankFormat {

    private final UltraHcPlugin plugin;

    public RankFormat(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    /** Prefiks rangi w formacie legacy (&), np. "&6#1 UHC &7[&65✪&7] ". */
    public String prefix(UUID uuid) {
        PlayerProfile p = plugin.profiles().get(uuid);
        int level = p == null ? 0 : p.getLevel();
        return podiumTag(uuid) + plugin.messages().raw("rank.prefix", Map.of("level", String.valueOf(level)));
    }

    private String podiumTag(UUID uuid) {
        if (plugin.leaderboards() == null) return "";
        List<Storage.LeaderboardEntry> top = plugin.leaderboards().top(Storage.LeaderboardType.LEVEL);
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            if (top.get(i).uuid().equals(uuid)) {
                return plugin.messages().raw("rank.podium-" + (i + 1));
            }
        }
        return "";
    }
}
