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

    private static final Map<String, Material> ICONS = Map.of(
            "gornik-pickaxe", Material.IRON_PICKAXE,
            "sharp-sword", Material.IRON_SWORD,
            "fire-sword", Material.IRON_SWORD,
            "hell-sword", Material.DIAMOND_SWORD,
            "enchanter", Material.ENCHANTING_TABLE,
            "pandora-box", Material.CHEST,
            "golden-head", Material.PLAYER_HEAD,
            "detector", Material.CLOCK);

    public void open(Player player) {
        MessagesManager msg = plugin.messages();
        ShopHolder holder = new ShopHolder();
        List<String> ids = plugin.shop().recipeIds();
        Inventory inv = Bukkit.createInventory(holder, 27, msg.component("shop.title", null));
        holder.inv = inv;

        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        int slot = 0;
        for (String id : ids) {
            long price = plugin.shop().price(id);
            boolean owned = profile != null && plugin.shop().owns(profile, id);
            inv.setItem(slot++, icon(id, plugin.shop().displayName(id), price, owned, msg));
        }
        // Pasek balansu XP na dole.
        long xp = profile != null ? profile.getCredits() : 0;
        ItemStack balance = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta bm = balance.getItemMeta();
        bm.displayName(LEGACY.deserialize(msg.raw("shop.balance", Map.of("amount", pl.ultrahc.paper.util.NumberUtil.grouped(xp)))));
        bm.lore(List.of(LEGACY.deserialize(msg.raw("shop.balance-lore"))));
        balance.setItemMeta(bm);
        inv.setItem(22, balance);

        GuiUtil.open(player, inv);
    }

    private ItemStack icon(String id, String name, long price, boolean owned, MessagesManager msg) {
        ItemStack item = new ItemStack(ICONS.getOrDefault(id, Material.PAPER));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("&6" + name));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : msg.rawList("shop.desc." + id)) lore.add(LEGACY.deserialize(line));
        lore.add(net.kyori.adventure.text.Component.empty());
        lore.add(LEGACY.deserialize(msg.raw("shop.price-line", Map.of("price", pl.ultrahc.paper.util.NumberUtil.grouped(price)))));
        lore.add(LEGACY.deserialize(owned ? msg.raw("shop.status-owned") : msg.raw("shop.status-buy")));
        meta.lore(lore);
        if (owned) meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
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
            pl.ultrahc.paper.util.Feedback.buy(player);
            open(player); // odswiez GUI (status)
        } else {
            player.sendMessage(msg.prefixed("currency.not-enough", Map.of(
                    "name", msg.raw("currency.name"),
                    "need", String.valueOf(price),
                    "have", String.valueOf(profile.getCredits()))));
            pl.ultrahc.paper.util.Feedback.error(player);
        }
    }
}
