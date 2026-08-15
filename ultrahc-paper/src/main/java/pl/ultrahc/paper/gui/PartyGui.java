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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.party.Party;

import java.util.List;
import java.util.UUID;

/** GUI party: sklad (glowki), przyciski Opusc / Rozwiaz, podpowiedz zaproszenia. */
public class PartyGui implements Listener {

    private final UltraHcPlugin plugin;
    private final NamespacedKey key;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public PartyGui(UltraHcPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "uhc_party_action");
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        Party party = plugin.party().get(player.getUniqueId());
        if (party == null) { player.sendMessage(plugin.messages().prefixed("party.not-in", null)); return; }
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27, plugin.messages().component("party.gui-title", null));
        holder.inv = inv;

        int slot = 0;
        for (UUID id : party.getMembers()) {
            if (slot >= 18) break;
            inv.setItem(slot++, memberHead(id, party.isLeader(id)));
        }
        inv.setItem(18, button("hint", Material.PAPER, plugin.messages().raw("party.gui-invite-hint")));
        inv.setItem(22, button("leave", Material.BARRIER, plugin.messages().raw("party.gui-leave")));
        if (party.isLeader(player.getUniqueId())) {
            inv.setItem(26, button("disband", Material.TNT, plugin.messages().raw("party.gui-disband")));
        }
        GuiUtil.open(player, inv);
    }

    private ItemStack memberHead(UUID id, boolean leader) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        var off = plugin.getServer().getOfflinePlayer(id);
        meta.setOwningPlayer(off);
        meta.displayName(LEGACY.deserialize((leader ? "&e★ " : "&7") + (off.getName() != null ? off.getName() : "gracz")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(String action, Material mat, String name) {
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
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (action == null) return;
        switch (action) {
            case "leave" -> { plugin.party().leave(player); player.closeInventory(); }
            case "disband" -> { plugin.party().disband(player); player.closeInventory(); }
            default -> {}
        }
    }
}
