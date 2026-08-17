package pl.ultrahc.paper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.config.MessagesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GUI statystyk gracza (podglad XP/PD/poziom/kille/wygrane/klasa). */
public class StatsGui implements Listener {

    private final UltraHcPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public StatsGui(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        MessagesManager msg = plugin.messages();
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) return;
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 9, msg.component("stats.gui-title", null));
        holder.inv = inv;

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(LEGACY.deserialize("&e" + player.getName()));
        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize(msg.raw("stats.xp", Map.of("value", pl.ultrahc.paper.util.NumberUtil.grouped(p.getCredits())))));
        lore.add(LEGACY.deserialize(msg.raw("stats.level", Map.of(
                "level", String.valueOf(p.getLevel()),
                "star", plugin.levels().starSymbol(),
                "current", pl.ultrahc.paper.util.NumberUtil.grouped(p.getProgressPoints()),
                "required", pl.ultrahc.paper.util.NumberUtil.grouped(plugin.levels().requiredForLevel(p.getLevel()))))));
        lore.add(LEGACY.deserialize(msg.raw("stats.kills", Map.of("value", String.valueOf(p.getKills())))));
        lore.add(LEGACY.deserialize(msg.raw("stats.wins", Map.of("value", String.valueOf(p.getWins())))));
        lore.add(LEGACY.deserialize(msg.raw("stats.clazz", Map.of("value", plugin.classes().displayName(p.getSelectedClass())))));
        meta.lore(lore);
        head.setItemMeta(meta);
        inv.setItem(4, head);
        GuiUtil.open(player, inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
