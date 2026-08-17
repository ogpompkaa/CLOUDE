package pl.ultrahc.paper.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rangi serwerowe (OWNER/ADMIN/MOD/HELPER/SVIP/VIP/GRACZ) — oparte na
 * uprawnieniach (nadawaj np. LuckPermsem: ultrahc.rank.owner itd.). Kazda ranga
 * ma prefiks i kolor nicku z config (ranks.groups). Gracz dostaje range o
 * najwyzszej wadze, do ktorej ma uprawnienie; GRACZ to domyslny fallback.
 *
 * <p>Uwaga: to system NIEZALEZNY od poziomu "gwiazdki" ({@link RankFormat}) —
 * pelny prefiks laczy range serwerowa z prefiksem poziomu/podium.
 */
public class GroupManager {

    public record Group(String id, String prefix, String nameColor, int weight, String permission, String trail) {}

    private final UltraHcPlugin plugin;
    private final List<Group> ranked = new ArrayList<>(); // grupy z uprawnieniem, malejaco wg wagi
    private Group defaultGroup = new Group("gracz", "&7", "&7", 0, "", "");

    public GroupManager(UltraHcPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        ranked.clear();
        defaultGroup = new Group("gracz", "&7", "&7", 0, "", "");
        ConfigurationSection sec = plugin.configManager().raw().getConfigurationSection("ranks.groups");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection g = sec.getConfigurationSection(id);
            if (g == null) continue;
            Group grp = new Group(id,
                    g.getString("prefix", "&7"),
                    g.getString("name-color", "&7"),
                    g.getInt("weight", 0),
                    g.getString("permission", ""),
                    g.getString("trail", ""));
            if (grp.permission() == null || grp.permission().isBlank()) {
                defaultGroup = grp; // ranga bez uprawnienia = domyslna (GRACZ)
            } else {
                ranked.add(grp);
            }
        }
        ranked.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
    }

    /** Ranga gracza: pierwsza (najwyzsza waga), do ktorej ma uprawnienie; inaczej GRACZ. */
    public Group of(Player player) {
        for (Group g : ranked) {
            if (player.hasPermission(g.permission())) return g;
        }
        // Fallback dla malego/testowego setupu bez LuckPerms: op = OWNER.
        if (player.isOp() && plugin.configManager().raw().getBoolean("ranks.op-is-owner", true)) {
            Group owner = ownerGroup();
            if (owner != null) return owner;
        }
        return defaultGroup;
    }

    /** Grupa OWNER (po id albo najwyzsza waga) — do fallbacku op. */
    private Group ownerGroup() {
        for (Group g : ranked) {
            if (g.id().equalsIgnoreCase("owner")) return g;
        }
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    public String nameColor(Player player) { return of(player).nameColor(); }

    /** Sam prefiks rangi serwerowej (np. "&6[VIP] ") — do nametagow/kanalow. */
    public String groupPrefix(Player player) { return of(player).prefix(); }

    /**
     * Pelny nick do wyswietlenia (czat/TAB) wg konfigurowalnego szablonu
     * chat.name-format z placeholderami %podium% %group% %level% %namecolor% %name%.
     * Dzieki temu uklad rang/poziomu jest w pelni edytowalny (bez podwojnych nawiasow).
     */
    public String displayName(Player player) {
        return formatName(player, "chat.name-format");
    }

    /** Nick do TAB (osobny format — np. #1 UHC za nickiem). */
    public String tabName(Player player) {
        return formatName(player, "chat.tab-format");
    }

    private String formatName(Player player, String formatKey) {
        var msg = plugin.messages();
        Group g = of(player);
        PlayerProfile prof = plugin.profiles().get(player.getUniqueId());
        int level = prof != null ? prof.getLevel() : 0;
        // Poziom 0 (nowy gracz) — bez znacznika, zeby nie zasmiecac nicku.
        String levelTag = level > 0 ? msg.raw("chat.level-tag", Map.of("level", String.valueOf(level))) : "";
        return msg.raw(formatKey)
                .replace("%podium%", plugin.rankFormat().podium(player.getUniqueId()))
                .replace("%group%", g.prefix())
                .replace("%level%", levelTag)
                .replace("%namecolor%", g.nameColor())
                .replace("%name%", player.getName())
                .trim();
    }

    /** Pelny prefiks (ranga + poziom/podium) — zachowane dla zgodnosci. */
    public String fullPrefix(Player player) {
        return of(player).prefix() + plugin.rankFormat().prefix(player.getUniqueId());
    }
}
