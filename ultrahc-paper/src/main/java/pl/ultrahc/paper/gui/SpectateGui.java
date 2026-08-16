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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.game.GameInstance;
import pl.ultrahc.paper.game.Team;

import java.util.Map;
import java.util.UUID;

/** Menu obserwatora: lista zywych graczy; klik = teleport do wybranego (spectator). */
public class SpectateGui implements Listener {

    private final UltraHcPlugin plugin;
    private final NamespacedKey targetKey;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public SpectateGui(UltraHcPlugin plugin) {
        this.plugin = plugin;
        this.targetKey = new NamespacedKey(plugin, "uhc_spectate_target");
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        GameInstance game = plugin.games() == null ? null : plugin.games().current();
        if (game == null || game.teams() == null) return;
        Holder holder = new Holder();
        int alive = game.teams().alivePlayers();
        int size = Math.max(9, Math.min(54, ((alive - 1) / 9 + 1) * 9));
        Inventory inv = Bukkit.createInventory(holder, size, plugin.messages().component("spectate.title", null));
        holder.inv = inv;

        int slot = 0;
        for (Team team : game.teams().aliveTeams()) {
            for (UUID id : team.getAlive()) {
                Player target = plugin.getServer().getPlayer(id);
                if (target == null || slot >= inv.getSize()) continue;
                inv.setItem(slot++, head(target));
            }
        }
        GuiUtil.open(player, inv);
    }

    private ItemStack head(Player target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(LEGACY.deserialize("&e" + target.getName()));
        meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target.getUniqueId().toString());
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
        String id = clicked.getItemMeta().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
        if (id == null) return;
        Player target = plugin.getServer().getPlayer(UUID.fromString(id));
        if (target != null) {
            GuiUtil.click(player);
            player.teleport(target.getLocation());
            player.sendMessage(plugin.messages().prefixed("spectate.teleported", Map.of("player", target.getName())));
        }
        player.closeInventory();
    }
}
