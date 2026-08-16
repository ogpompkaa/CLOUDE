package pl.ultrahc.paper.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Wspolne wykonczenie GUI: wypelnienie tla i dzwiek otwarcia. */
public final class GuiUtil {

    private GuiUtil() {}

    private static final ItemStack FILLER = filler();

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ")); // pusta nazwa, bez tooltipa
        item.setItemMeta(meta);
        return item;
    }

    /** Wypelnia puste sloty szara szybka (estetyczne tlo). */
    public static void fill(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, FILLER.clone());
        }
    }

    /** Otwiera GUI z dzwiekiem (delikatny klik). */
    public static void open(Player player, Inventory inv) {
        fill(inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
    }

    /** Delikatny dzwiek klikniecia w menu (tactile feedback nawigacji). */
    public static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.7f);
    }
}
