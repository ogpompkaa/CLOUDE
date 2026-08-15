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
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.config.MessagesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GUI sklepu receptur (Sklepikarz). Klik w recepture = proba zakupu za XP. */
public class ShopGui implements Listener {

    private final UltraHcPlugin plugin;
    private final NamespacedKey shopKey;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public ShopGui(UltraHcPlugin plugin) {
        this.plugin = plugin;
        this.shopKey = new NamespacedKey(plugin, "uhc_shop");
    }

    /** Marker holdera, po ktorym rozpoznajemy nasze GUI. */
    private static final class ShopHolder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        MessagesManager msg = plugin.messages();
        ShopHolder holder = new ShopHolder();
        List<String> ids = plugin.shop().recipeIds();
        int size = ((ids.size() / 9) + 1) * 9;
        Inventory inv = Bukkit.createInventory(holder, Math.max(9, size), msg.component("shop.title", null));
        holder.inv = inv;

        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        int slot = 0;
        for (String id : ids) {
            long price = plugin.shop().price(id);
            boolean owned = profile != null && plugin.shop().owns(profile, id);
            inv.setItem(slot++, icon(id, plugin.shop().displayName(id), price, owned));
        }
        player.openInventory(inv);
    }

    private ItemStack icon(String id, String name, long price, boolean owned) {
        ItemStack item = new ItemStack(owned ? Material.LIME_DYE : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("&6" + name));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("&7Cena: &e" + price + " XP"));
        lore.add(LEGACY.deserialize(owned ? "&aPosiadasz" : "&eKliknij, aby kupic"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ShopHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer().get(shopKey, PersistentDataType.STRING);
        if (id == null) return;

        MessagesManager msg = plugin.messages();
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        if (profile == null) return;
        if (plugin.shop().owns(profile, id)) {
            player.sendMessage(msg.prefixed("shop.already-owned-recipe", null));
            return;
        }
        long price = plugin.shop().price(id);
        if (plugin.shop().buy(profile, id)) {
            player.sendMessage(msg.prefixed("shop.bought-recipe", Map.of(
                    "recipe", plugin.shop().displayName(id),
                    "price", String.valueOf(price),
                    "currency", msg.raw("currency.name"))));
            open(player); // odswiez GUI (status)
        } else {
            player.sendMessage(msg.prefixed("currency.not-enough", Map.of(
                    "name", msg.raw("currency.name"),
                    "need", String.valueOf(price),
                    "have", String.valueOf(profile.getCredits()))));
        }
    }
}
