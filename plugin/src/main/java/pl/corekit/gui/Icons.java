package pl.corekit.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Builds menu icons with non-italic names/lore (MC italicises custom names by default). */
public final class Icons {

    private Icons() {
    }

    public static ItemStack of(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore.stream()
                        .map(line -> line.decoration(TextDecoration.ITALIC, false))
                        .toList());
            }
        });
        return item;
    }
}
