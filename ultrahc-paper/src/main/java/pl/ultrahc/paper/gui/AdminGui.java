package pl.ultrahc.paper.gui;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.ultrahc.paper.UltraHcPlugin;

/** Panel admina: kafelki Sezon / Instancje. */
public class AdminGui implements Listener {

    private final UltraHcPlugin plugin;
    private final NamespacedKey key;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public AdminGui(UltraHcPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "uhc_admin");
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 9, plugin.messages().component("admin.panel-title", null));
        holder.inv = inv;
        inv.setItem(2, tile("season", Material.GOLD_BLOCK, plugin.messages().raw("admin.panel-season")));
        inv.setItem(6, tile("instances", Material.COMMAND_BLOCK, plugin.messages().raw("admin.panel-instances")));
        player.openInventory(inv);
    }

    private ItemStack tile(String action, Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player) || !player.hasPermission("ultrahc.admin")) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (action == null) return;
        player.closeInventory();
        switch (action) {
            case "season" -> plugin.seasonGui().open(player);
            case "instances" -> plugin.instanceAdminGui().open(player);
            default -> {}
        }
    }
}
