package pl.ultrahc.paper.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.ultrahc.paper.UltraHcPlugin;

/** Uzycie kompasu -> wskazanie sojusznika/przeciwnika (logika w CompassManager). */
public class CompassListener implements Listener {

    private final UltraHcPlugin plugin;

    public CompassListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || (item.getType() != Material.COMPASS && item.getType() != Material.RECOVERY_COMPASS)) return;
        if (plugin.compass() == null) return;
        plugin.compass().handleUse(e.getPlayer());
    }
}
