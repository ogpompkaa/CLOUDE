package pl.ultrahc.paper.listener;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import pl.ultrahc.paper.UltraHcPlugin;

/**
 * Poczekalnia areny: przedmiot "Powrot do huba" i jego obsluga (transfer na
 * serwer huba przez kanal BungeeCord/Velocity 'Connect'). Dzieki temu gracz,
 * ktory auto-dolaczyl do gry, moze wrocic do glownego lobby przed startem.
 */
public class ArenaLobbyListener implements Listener {

    public static final String BUNGEE_CHANNEL = "BungeeCord";
    private final UltraHcPlugin plugin;
    private final NamespacedKey hubKey;

    public ArenaLobbyListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
        this.hubKey = new NamespacedKey(plugin, "uhc_hub_item");
    }

    /** Przedmiot "Powrot do huba" wkladany do reki w poczekalni. */
    public ItemStack hubItem() {
        ItemStack item = new ItemStack(Material.RED_BED);
        var meta = item.getItemMeta();
        meta.displayName(plugin.messages().component("arena.hub-item", null));
        meta.getPersistentDataContainer().set(hubKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Kladzie przedmiot huba do slotu 8 (tylko w poczekalni areny). */
    public void giveHubItem(Player player) {
        player.getInventory().setItem(8, hubItem());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;
        if (!item.getItemMeta().getPersistentDataContainer().has(hubKey, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        Player player = e.getPlayer();
        // Wyjscie z gry (jesli w niej byl) + transfer na hub.
        if (plugin.games() != null) plugin.games().leave(player.getUniqueId());
        connectHub(player);
    }

    /** Transfer gracza na serwer huba (nazwa z network.hub-server). */
    public void connectHub(Player player) {
        String hub = plugin.configManager().raw().getString("network.hub-server", "lobby");
        player.sendMessage(plugin.messages().prefixed("arena.returning-hub", null));
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(hub);
        player.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
    }
}
