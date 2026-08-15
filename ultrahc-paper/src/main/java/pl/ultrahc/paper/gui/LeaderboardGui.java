package pl.ultrahc.paper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.ultrahc.common.storage.Storage;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GUI topek: Top 10 zabojstw / wygranych / poziomow (podglad). */
public class LeaderboardGui implements Listener {

    private final UltraHcPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public LeaderboardGui(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 9, plugin.messages().component("leaderboard-gui.title", null));
        holder.inv = inv;
        inv.setItem(2, icon(Material.DIAMOND_SWORD, "leaderboard-gui.kills-icon", Storage.LeaderboardType.KILLS));
        inv.setItem(4, icon(Material.GOLDEN_APPLE, "leaderboard-gui.wins-icon", Storage.LeaderboardType.WINS));
        inv.setItem(6, icon(Material.NETHER_STAR, "leaderboard-gui.level-icon", Storage.LeaderboardType.LEVEL));
        player.openInventory(inv);
    }

    private ItemStack icon(Material mat, String titleKey, Storage.LeaderboardType type) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(plugin.messages().raw(titleKey)));
        List<Component> lore = new ArrayList<>();
        List<Storage.LeaderboardEntry> top = plugin.leaderboards() == null ? List.of() : plugin.leaderboards().top(type);
        if (top.isEmpty()) {
            lore.add(LEGACY.deserialize(plugin.messages().raw("leaderboard.empty")));
        } else {
            int pos = 1;
            for (Storage.LeaderboardEntry e : top) {
                lore.add(LEGACY.deserialize(plugin.messages().raw("leaderboard.entry", Map.of(
                        "pos", String.valueOf(pos++), "name", e.name(), "value", String.valueOf(e.value())))));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
