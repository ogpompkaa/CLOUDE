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
        var defs = plugin.quests().defs();
        int size = Math.max(9, ((defs.size() / 9) + 1) * 9);
        Inventory inv = Bukkit.createInventory(holder, size, msg.component("quest.gui-title", null));
        holder.inv = inv;

        int slot = 0;
        for (QuestsManager.QuestDef def : defs.values()) {
            Storage.QuestRecord rec = plugin.quests().record(player.getUniqueId(), def);
            inv.setItem(slot++, icon(def, rec, msg));
        }
        player.openInventory(inv);
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
        }
        lore.add(LEGACY.deserialize(msg.raw("quest.reward-line", Map.of(
                "xp", String.valueOf(def.rewardXp()),
                "pd", String.valueOf(def.rewardPd())))));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof QuestHolder) {
            e.setCancelled(true); // tylko podglad
        }
    }
}
