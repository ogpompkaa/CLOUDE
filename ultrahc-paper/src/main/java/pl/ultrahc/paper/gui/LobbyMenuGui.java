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
import pl.ultrahc.paper.config.MessagesManager;

import java.util.List;

/** Menu glowne lobby (Hub): kafelki Graj / Klasy / Sklep / Questy / Statystyki. */
public class LobbyMenuGui implements Listener {

    private final UltraHcPlugin plugin;
    private final NamespacedKey menuKey;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public LobbyMenuGui(UltraHcPlugin plugin) {
        this.plugin = plugin;
        this.menuKey = new NamespacedKey(plugin, "uhc_menu");
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        MessagesManager msg = plugin.messages();
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 9, msg.component("menu.title", null));
        holder.inv = inv;
        inv.setItem(0, tile("play", Material.COMPASS, msg.raw("menu.play"), msg.raw("menu.play-lore")));
        inv.setItem(2, tile("classes", Material.IRON_CHESTPLATE, msg.raw("menu.classes"), msg.raw("menu.classes-lore")));
        inv.setItem(4, tile("shop", Material.EMERALD, msg.raw("menu.shop"), msg.raw("menu.shop-lore")));
        inv.setItem(6, tile("quests", Material.WRITABLE_BOOK, msg.raw("menu.quests"), msg.raw("menu.quests-lore")));
        inv.setItem(8, tile("stats", Material.PLAYER_HEAD, msg.raw("menu.stats"), msg.raw("menu.stats-lore")));
        player.openInventory(inv);
    }

    private ItemStack tile(String action, Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        meta.lore(List.of(LEGACY.deserialize(lore)));
        meta.getPersistentDataContainer().set(menuKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(menuKey, PersistentDataType.STRING);
        if (action == null) return;
        player.closeInventory();
        switch (action) {
            case "play" -> plugin.arenaSelect().open(player);
            case "classes" -> plugin.classGui().open(player);
            case "shop" -> plugin.shopGui().open(player);
            case "quests" -> plugin.questGui().open(player);
            case "stats" -> plugin.statsGui().open(player);
            default -> {}
        }
    }
}
