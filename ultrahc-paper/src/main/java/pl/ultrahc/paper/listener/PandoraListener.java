package pl.ultrahc.paper.listener;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Puszka Pandory: po uzyciu losowo wypada ksiazka z enchantem albo dopada
 * gracza chwilowa klatwa (negatywny efekt). Zjada jedna sztuke.
 */
public class PandoraListener implements Listener {

    private final UltraHcPlugin plugin;

    private static final List<Enchantment> GOOD_BOOKS = List.of(
            Enchantment.SHARPNESS, Enchantment.PROTECTION, Enchantment.UNBREAKING, Enchantment.EFFICIENCY);
    private static final List<PotionEffectType> CURSES = List.of(
            PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.NAUSEA, PotionEffectType.BLINDNESS);

    public PandoraListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (plugin.recipes() == null) return;
        ItemStack item = e.getItem();
        if (!"pandora-box".equals(plugin.recipes().recipeIdOf(item))) return;

        e.setCancelled(true);
        Player player = e.getPlayer();
        item.setAmount(item.getAmount() - 1);

        if (ThreadLocalRandom.current().nextBoolean()) {
            // Nagroda: ksiazka z losowym enchantem.
            Enchantment ench = GOOD_BOOKS.get(ThreadLocalRandom.current().nextInt(GOOD_BOOKS.size()));
            player.getInventory().addItem(book(ench));
            player.sendMessage(plugin.messages().prefixed("pandora.reward", Map.of("item", ench.getKey().getKey())));
        } else {
            // Klatwa: losowy negatywny efekt na 8 s.
            PotionEffectType curse = CURSES.get(ThreadLocalRandom.current().nextInt(CURSES.size()));
            player.addPotionEffect(new PotionEffect(curse, 8 * 20, 0));
            player.sendMessage(plugin.messages().prefixed("pandora.curse", null));
        }
    }

    private ItemStack book(Enchantment ench) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
        meta.addStoredEnchant(ench, 1, true);
        item.setItemMeta(meta);
        return item;
    }
}
