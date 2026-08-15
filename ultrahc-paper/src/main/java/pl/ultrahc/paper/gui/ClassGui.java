package pl.ultrahc.paper.gui;

import net.kyori.adventure.text.Component;
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

/** GUI klas: podglad kitu/umiejetnosci, kup za XP lub wybierz. */
public class ClassGui implements Listener {

    private final UltraHcPlugin plugin;
    private final NamespacedKey classKey;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static final Map<String, Material> ICONS = Map.of(
            "civil", Material.BREAD,
            "gornik", Material.STONE_PICKAXE,
            "sniper", Material.BOW,
            "chef", Material.COOKED_BEEF,
            "fisher", Material.FISHING_ROD,
            "yeti", Material.IRON_SHOVEL,
            "smith", Material.IRON_INGOT,
            "guard", Material.SHIELD);

    public ClassGui(UltraHcPlugin plugin) {
        this.plugin = plugin;
        this.classKey = new NamespacedKey(plugin, "uhc_class");
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        MessagesManager msg = plugin.messages();
        Holder holder = new Holder();
        List<String> ids = plugin.classes().ids();
        int size = Math.max(9, ((ids.size() - 1) / 9 + 1) * 9);
        Inventory inv = Bukkit.createInventory(holder, size, msg.component("class.gui-title", null));
        holder.inv = inv;

        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        int slot = 0;
        for (String id : ids) {
            inv.setItem(slot++, icon(id, p, msg));
        }
        player.openInventory(inv);
    }

    private ItemStack icon(String id, PlayerProfile p, MessagesManager msg) {
        boolean owned = p != null && plugin.classes().isUnlocked(p, id);
        boolean selected = p != null && id.equals(p.getSelectedClass());
        ItemStack item = new ItemStack(ICONS.getOrDefault(id, Material.PAPER));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("&e" + plugin.classes().displayName(id)));

        List<Component> lore = new ArrayList<>();
        for (String line : msg.rawList("class.desc." + id)) lore.add(LEGACY.deserialize(line));
        lore.add(Component.empty());
        if (!owned) lore.add(LEGACY.deserialize(msg.raw("class.price-line", Map.of("price", String.valueOf(plugin.classes().price(id))))));
        lore.add(LEGACY.deserialize(selected ? msg.raw("class.status-selected")
                : owned ? msg.raw("class.status-owned") : msg.raw("class.status-locked")));
        meta.lore(lore);
        if (selected || owned) meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(classKey, PersistentDataType.STRING, id);
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
        String id = clicked.getItemMeta().getPersistentDataContainer().get(classKey, PersistentDataType.STRING);
        if (id == null) return;

        MessagesManager msg = plugin.messages();
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) return;

        if (plugin.classes().isUnlocked(p, id)) {
            plugin.classes().select(p, id);
            player.sendMessage(msg.prefixed("class.selected", Map.of("class", plugin.classes().displayName(id))));
            pl.ultrahc.paper.util.Feedback.buy(player);
        } else {
            long price = plugin.classes().price(id);
            if (plugin.classes().buy(p, id)) {
                plugin.classes().select(p, id);
                player.sendMessage(msg.prefixed("class.bought", Map.of(
                        "class", plugin.classes().displayName(id),
                        "price", String.valueOf(price),
                        "currency", msg.raw("currency.name"))));
                pl.ultrahc.paper.util.Feedback.buy(player);
            } else {
                player.sendMessage(msg.prefixed("currency.not-enough", Map.of(
                        "name", msg.raw("currency.name"),
                        "need", String.valueOf(price),
                        "have", String.valueOf(p.getCredits()))));
                pl.ultrahc.paper.util.Feedback.error(player);
            }
        }
        open(player); // odswiez statusy
    }
}
