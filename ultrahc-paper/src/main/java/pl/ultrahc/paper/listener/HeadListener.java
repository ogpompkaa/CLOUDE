package pl.ultrahc.paper.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.ultrahc.paper.UltraHcPlugin;

/** Konsumpcja glowki: PPM z glowka UHC -> efekty z config, zjada jedna sztuke. */
public class HeadListener implements Listener {

    private final UltraHcPlugin plugin;

    public HeadListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (plugin.heads() == null) return;

        ItemStack item = e.getItem();
        String type = plugin.heads().headType(item);
        if (type == null) return;

        e.setCancelled(true); // nie stawiaj glowki jako bloku
        Player player = e.getPlayer();
        plugin.heads().applyEffects(player, type);
        item.setAmount(item.getAmount() - 1); // zjedz jedna
        player.sendMessage(plugin.messages().prefixed("head.consumed", null));
    }
}
