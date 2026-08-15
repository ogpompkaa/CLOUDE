package pl.corekit.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Single listener that drives every {@link Menu}. Any interaction with an
 * inventory owned by a {@code Menu} holder is cancelled outright — this blocks
 * item theft via shift-click, number keys, drag and drop-outside — and genuine
 * clicks on a top-inventory slot are forwarded to the menu's slot handler.
 */
public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        // Cancel unconditionally: covers clicks in the player's own inventory
        // while the menu is open (shift-click would otherwise move items in).
        event.setCancelled(true);

        if (event.getClickedInventory() == null) {
            return;
        }
        // Only slots belonging to the menu (the top inventory) carry handlers.
        if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            menu.handleClick(event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
