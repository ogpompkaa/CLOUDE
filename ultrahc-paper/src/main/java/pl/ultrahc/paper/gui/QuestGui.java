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
import pl.ultrahc.common.storage.Storage;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.config.MessagesManager;
import pl.ultrahc.paper.manager.QuestsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GUI questow (Krzysiu). Tylko podglad — nagrody przyznawane automatycznie po ukonczeniu. */
public class QuestGui implements Listener {

    private final UltraHcPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public QuestGui(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class QuestHolder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player player) {
        MessagesManager msg = plugin.messages();
        QuestHolder holder = new QuestHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, msg.component("quest.gui-title", null));
        holder.inv = inv;

        // Rzedy wg typu: dzienne (0-8), tygodniowe (9-17), stale (18-26).
        int[] next = {0, 9, 18};
        for (QuestsManager.QuestDef def : plugin.quests().defs().values()) {
            int row = switch (def.type()) {
                case DAILY -> 0;
                case WEEKLY -> 1;
                case PERMANENT -> 2;
            };
            if (next[row] >= (row + 1) * 9) continue; // rzad pelny
            Storage.QuestRecord rec = plugin.quests().record(player.getUniqueId(), def);
            inv.setItem(next[row]++, icon(def, rec, msg));
        }
        GuiUtil.open(player, inv);
    }

    private ItemStack icon(QuestsManager.QuestDef def, Storage.QuestRecord rec, MessagesManager msg) {
        boolean done = rec.completed();
        ItemStack item = new ItemStack(done ? Material.LIME_DYE : Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("&6" + def.name()));

        String typeKey = switch (def.type()) {
            case DAILY -> "quest.type-daily";
            case WEEKLY -> "quest.type-weekly";
            case PERMANENT -> "quest.type-permanent";
        };
        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize(msg.raw(typeKey)));
        if (done) {
            lore.add(LEGACY.deserialize(msg.raw("quest.done")));
        } else {
            lore.add(LEGACY.deserialize(msg.raw("quest.progress", Map.of(
                    "progress", String.valueOf(rec.progress()),
                    "target", String.valueOf(def.target())))));
            lore.add(LEGACY.deserialize(progressBar(rec.progress(), def.target(), msg)));
        }
        lore.add(LEGACY.deserialize(msg.raw("quest.reward-line", Map.of(
                "xp", String.valueOf(def.rewardXp()),
                "pd", String.valueOf(def.rewardPd())))));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Pasek postepu np. [▰▰▰▰▱▱▱▱▱▱] 40%. */
    private String progressBar(long progress, long target, MessagesManager msg) {
        int segments = 10;
        int filled = target <= 0 ? segments : (int) Math.min(segments, Math.round((double) progress / target * segments));
        int percent = target <= 0 ? 100 : (int) Math.min(100, Math.round((double) progress / target * 100));
        String fill = msg.raw("quest-bar.filled");
        String empty = msg.raw("quest-bar.empty");
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < segments; i++) bar.append(i < filled ? fill : empty);
        return msg.raw("quest-bar.line", Map.of("bar", bar.toString(), "percent", String.valueOf(percent)));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof QuestHolder) {
            e.setCancelled(true); // tylko podglad
        }
    }
}

