package pl.corekit.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/** Builds menu icons with non-italic names/lore (MC italicises custom names by default). */
public final class Icons {

    private Icons() {
    }

    public static ItemStack of(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> applyText(meta, name, lore));
        return item;
    }

    /** A player-head icon skinned with {@code owner}'s texture. */
    public static ItemStack head(OfflinePlayer owner, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        item.editMeta(SkullMeta.class, meta -> {
            meta.setOwningPlayer(owner);
            applyText(meta, name, lore);
        });
        return item;
    }

    private static void applyText(ItemMeta meta, Component name, List<Component> lore) {
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(line -> line.decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
    }
}
