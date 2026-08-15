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
import pl.ultrahc.common.instances.InstanceInfo;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.config.MessagesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Panel admina: lista instancji z rejestru; klik = zadanie zamkniecia (cross-server). */
public class InstanceAdminGui implements Listener {

    private final UltraHcPlugin plugin;
    private final NamespacedKey idKey;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public InstanceAdminGui(UltraHcPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "uhc_instance_id");
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        MessagesManager msg = plugin.messages();
        List<InstanceInfo> list = plugin.instances() == null ? List.of() : plugin.instances().listAll();
        Holder holder = new Holder();
        int size = Math.max(9, Math.min(54, ((list.size() - 1) / 9 + 1) * 9));
        Inventory inv = Bukkit.createInventory(holder, size, msg.component("admin.instances-title", null));
        holder.inv = inv;

        if (list.isEmpty()) player.sendMessage(msg.prefixed("admin.instances-empty", null));
        int slot = 0;
        for (InstanceInfo info : list) {
            if (slot >= inv.getSize()) break;
            inv.setItem(slot++, icon(info, msg));
        }
        GuiUtil.open(player, inv);
    }

    private ItemStack icon(InstanceInfo info, MessagesManager msg) {
        ItemStack item = new ItemStack("RUNNING".equals(info.state()) ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("&e" + info.id()));
        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize(msg.raw("admin.instance-state", Map.of("state", info.state()))));
        lore.add(LEGACY.deserialize(msg.raw("admin.instance-players", Map.of(
                "players", String.valueOf(info.players()), "max", String.valueOf(info.maxPlayers())))));
        lore.add(LEGACY.deserialize(msg.raw("admin.instance-close-hint")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, info.id());
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
        String id = clicked.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (id == null) return;
        plugin.instances().requestClose(id);
        player.sendMessage(plugin.messages().prefixed("admin.instance-close-requested", Map.of("id", id)));
        player.closeInventory();
    }
}
