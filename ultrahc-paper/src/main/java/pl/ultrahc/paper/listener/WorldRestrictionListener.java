package pl.ultrahc.paper.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;
import pl.ultrahc.paper.UltraHcPlugin;

/**
 * Wylaczenie Netheru i Endu (UHC): portale netheru nie powstaja i nie przenosza,
 * portal koncowy nie da sie aktywowac ani przez niego przejsc. Sterowane
 * world.disable-nether / world.disable-end.
 */
public class WorldRestrictionListener implements Listener {

    private final UltraHcPlugin plugin;

    public WorldRestrictionListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean netherOff() { return plugin.configManager().raw().getBoolean("world.disable-nether", true); }
    private boolean endOff() { return plugin.configManager().raw().getBoolean("world.disable-end", true); }

    /** Blokada przejscia gracza przez portal (nether/end). */
    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        switch (e.getCause()) {
            case NETHER_PORTAL -> { if (netherOff()) e.setCancelled(true); }
            case END_PORTAL, END_GATEWAY -> { if (endOff()) e.setCancelled(true); }
            default -> { }
        }
    }

    /** Blokada przenoszenia encji/przedmiotow przez portale. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (netherOff() || endOff()) e.setCancelled(true);
    }

    /** Portale netheru w ogole nie powstaja (podpalenie obsydianu bez efektu). */
    @EventHandler(ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent e) {
        if (netherOff() && (e.getReason() == PortalCreateEvent.CreateReason.FIRE
                || e.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR)) {
            e.setCancelled(true);
        }
    }

    /** Blokada skrzyni Endera (brak wspoldzielonego/bezpiecznego schowka miedzy smierciami). */
    @EventHandler(ignoreCancelled = true)
    public void onEnderChest(InventoryOpenEvent e) {
        if (!plugin.configManager().raw().getBoolean("game.disable-enderchest", true)) return;
        if (e.getInventory().getType() == InventoryType.ENDER_CHEST) {
            e.setCancelled(true);
            if (e.getPlayer() instanceof Player p) {
                p.sendMessage(plugin.messages().prefixed("game.enderchest-blocked", null));
            }
        }
    }

    /** Nie da sie aktywowac portalu koncowego (oko endera w ramce). */
    @EventHandler(ignoreCancelled = true)
    public void onEyePlace(PlayerInteractEvent e) {
        if (!endOff()) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() == Material.END_PORTAL_FRAME
                && e.getItem() != null && e.getItem().getType() == Material.ENDER_EYE) {
            e.setCancelled(true);
        }
    }
}
