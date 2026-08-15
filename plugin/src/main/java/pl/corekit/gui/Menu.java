package pl.corekit.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal, reusable chest-menu built on the {@link InventoryHolder} pattern.
 *
 * <p>Identifying our menus by their holder ({@code getHolder() instanceof Menu})
 * rather than by title is what makes the click handling robust: titles can be
 * duplicated or spoofed, holders cannot. Each slot maps to an optional click
 * handler; {@link MenuListener} cancels every interaction inside the menu (so
 * items can't be dragged out) and dispatches to the handler for the clicked
 * slot.
 */
public class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();

    public Menu(Component title, int size) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    /** Places a clickable button. A {@code null} handler makes it decorative. */
    public void setButton(int slot, ItemStack item, Consumer<InventoryClickEvent> handler) {
        inventory.setItem(slot, item);
        if (handler != null) {
            handlers.put(slot, handler);
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** Dispatched by {@link MenuListener} for a click on a top-inventory slot. */
    void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> handler = handlers.get(event.getRawSlot());
        if (handler != null) {
            handler.accept(event);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
