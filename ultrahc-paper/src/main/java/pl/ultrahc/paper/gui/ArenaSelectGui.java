package pl.ultrahc.paper.gui;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
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

/**
 * GUI wyboru areny (kompas w lobby). Listuje dolaczalne instancje z rejestru;
 * klik = transfer gracza na dany arena-serwer (kanal BungeeCord/Velocity 'Connect').
 */
public class ArenaSelectGui implements Listener {

    public static final String BUNGEE_CHANNEL = "BungeeCord";
    private final UltraHcPlugin plugin;
    private final NamespacedKey targetKey;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public ArenaSelectGui(UltraHcPlugin plugin) {
        this.plugin = plugin;
        this.targetKey = new NamespacedKey(plugin, "uhc_arena_target");
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        MessagesManager msg = plugin.messages();
        int teamSize = plugin.configManager().raw().getInt("game.team-size", 1);
        List<InstanceInfo> list = plugin.instances() == null ? List.of() : plugin.instances().joinable(teamSize);

        Holder holder = new Holder();
        int size = Math.max(18, ((list.size() / 9) + 2) * 9);
        Inventory inv = Bukkit.createInventory(holder, Math.min(54, size), msg.component("arena.gui-title", null));
        holder.inv = inv;

        // Szybkie dolaczenie (pierwsza z listy).
        ItemStack quick = new ItemStack(Material.NETHER_STAR);
        ItemMeta qm = quick.getItemMeta();
        qm.displayName(LEGACY.deserialize(msg.raw("arena.quick")));
        qm.lore(List.of(LEGACY.deserialize(msg.raw("arena.quick-lore"))));
        if (!list.isEmpty()) qm.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, list.get(0).id());
        quick.setItemMeta(qm);
        inv.setItem(4, quick);

        if (list.isEmpty()) {
            player.sendMessage(msg.prefixed("arena.none", null));
        }
        int slot = 9;
        for (InstanceInfo info : list) {
            if (slot >= inv.getSize()) break;
            inv.setItem(slot++, instanceIcon(info, msg));
        }
        GuiUtil.open(player, inv);
    }

    private ItemStack instanceIcon(InstanceInfo info, MessagesManager msg) {
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(msg.raw("arena.instance", Map.of("id", info.id()))));
        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize(msg.raw("arena.lore-state", Map.of("state", info.state()))));
        lore.add(LEGACY.deserialize(msg.raw("arena.lore-players", Map.of(
                "players", String.valueOf(info.players()), "max", String.valueOf(info.maxPlayers())))));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, info.id());
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
        String target = clicked.getItemMeta().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
        if (target == null || target.isBlank()) return;
        player.sendMessage(plugin.messages().prefixed("arena.connecting", Map.of("id", target)));
        connectToServer(player, target);
        player.closeInventory();
    }

    /** Transfer gracza na inny serwer sieci (Velocity/BungeeCord 'Connect'). */
    public void connectToServer(Player player, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        player.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
    }
}
