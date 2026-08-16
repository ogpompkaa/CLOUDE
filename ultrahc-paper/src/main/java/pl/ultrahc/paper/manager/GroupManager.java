package pl.ultrahc.paper.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.ArrayList;
import java.util.List;

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

    public record Group(String id, String prefix, String nameColor, int weight, String permission) {}

    private final UltraHcPlugin plugin;
    private final List<Group> ranked = new ArrayList<>(); // grupy z uprawnieniem, malejaco wg wagi
    private Group defaultGroup = new Group("gracz", "&7", "&7", 0, "");

    public GroupManager(UltraHcPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        ranked.clear();
        defaultGroup = new Group("gracz", "&7", "&7", 0, "");
        ConfigurationSection sec = plugin.configManager().raw().getConfigurationSection("ranks.groups");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection g = sec.getConfigurationSection(id);
            if (g == null) continue;
            Group grp = new Group(id,
                    g.getString("prefix", "&7"),
                    g.getString("name-color", "&7"),
                    g.getInt("weight", 0),
                    g.getString("permission", ""));
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
        return defaultGroup;
    }

    public String nameColor(Player player) { return of(player).nameColor(); }

    /** Pelny prefiks do wyswietlenia: ranga serwerowa + prefiks poziomu/podium. */
    public String fullPrefix(Player player) {
        return of(player).prefix() + plugin.rankFormat().prefix(player.getUniqueId());
    }
}
