package pl.ultrahc.paper.gui;

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
import pl.ultrahc.paper.UltraHcPlugin;

/** GUI admina do zarzadzania sezonem: start / koniec (wyplata Top 3 + reset). */
public class SeasonAdminGui implements Listener {

    private final UltraHcPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public SeasonAdminGui(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 9, plugin.messages().component("season.gui-title", null));
        holder.inv = inv;
        inv.setItem(2, button(Material.LIME_CONCRETE, "&aRozpocznij sezon"));
        inv.setItem(6, button(Material.RED_CONCRETE, "&cZakoncz sezon (wyplata Top 3 + reset)"));
        GuiUtil.open(player, inv);
    }

    private ItemStack button(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("ultrahc.admin")) return;
        if (e.getSlot() == 2) {
            plugin.season().startSeason(player);
            player.closeInventory();
        } else if (e.getSlot() == 6) {
            plugin.season().endSeason(player);
            player.closeInventory();
        }
    }
}
