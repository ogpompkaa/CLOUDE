package pl.ultrahc.paper.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;

/**
 * Ekspansja PlaceholderAPI wystawiajaca dane profilu gracza pod {@code %ultrahc_...%}
 * (do uzycia w TAB, hologramach, innych pluginach). Klasa ladowana wylacznie gdy
 * PlaceholderAPI jest obecne — instancjonowana tylko z {@link PlaceholderIntegration}.
 */
public class UltraHcExpansion extends PlaceholderExpansion {

    private final UltraHcPlugin plugin;

    public UltraHcExpansion(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "ultrahc"; }
    @Override public String getAuthor() { return "UltraHC"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; } // przetrwaj /papi reload

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) return "";
        return switch (params.toLowerCase()) {
            case "level" -> String.valueOf(p.getLevel());
            case "xp", "credits" -> String.valueOf(p.getCredits());
            case "pd" -> String.valueOf(p.getProgressPoints());
            case "pd_required" -> String.valueOf(plugin.levels().requiredForLevel(p.getLevel()));
            case "kills" -> String.valueOf(p.getKills());
            case "wins" -> String.valueOf(p.getWins());
            case "class" -> p.getSelectedClass() == null ? "" : p.getSelectedClass();
            case "star" -> plugin.levels().starLabel(p);
            case "rank" -> plugin.messages().legacyStrip(plugin.rankFormat().prefix(player.getUniqueId()));
            case "party_size" -> String.valueOf(partySize(player));
            case "party_leader" -> String.valueOf(plugin.party() != null && plugin.party().isLeader(player.getUniqueId()));
            default -> null; // null = placeholder nieznany (PAPI zostawi surowy tekst)
        };
    }

    private int partySize(OfflinePlayer player) {
        if (plugin.party() == null) return 0;
        var party = plugin.party().get(player.getUniqueId());
        return party == null ? 0 : party.size();
    }
}
