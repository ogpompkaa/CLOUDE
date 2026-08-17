package pl.ultrahc.paper.listener;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.ultrahc.paper.UltraHcPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Puszka Pandory: stawiasz ją jako skrzynkę, a po kliknięciu PPM otwierasz —
 * losowo wypada książka z enchantem albo dopada Cię klątwa. Otwarcie zużywa skrzynkę.
 */
public class PandoraListener implements Listener {

    private final UltraHcPlugin plugin;
    private final NamespacedKey blockKey;

    private static final List<Enchantment> GOOD_BOOKS = List.of(
            Enchantment.SHARPNESS, Enchantment.PROTECTION, Enchantment.UNBREAKING, Enchantment.EFFICIENCY);
    private static final List<PotionEffectType> CURSES = List.of(
            PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.NAUSEA, PotionEffectType.BLINDNESS);

    public PandoraListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
        this.blockKey = new NamespacedKey(plugin, "uhc_pandora_block");
    }

    /** Postawienie Puszki Pandory: oznacz skrzynke tagiem w jej TileState. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.recipes() == null) return;
        if (!"pandora-box".equals(plugin.recipes().recipeIdOf(e.getItemInHand()))) return;
        if (e.getBlock().getState() instanceof TileState state) {
            state.getPersistentDataContainer().set(blockKey, PersistentDataType.BYTE, (byte) 1);
            state.update();
            e.getPlayer().sendMessage(plugin.messages().prefixed("pandora.placed", null));
        }
    }

    /** Otwarcie postawionej Puszki: losowy efekt, skrzynka znika. */
    @EventHandler
    public void onOpen(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block block = e.getClickedBlock();
        if (!(block.getState() instanceof TileState state)) return;
        if (!state.getPersistentDataContainer().has(blockKey, PersistentDataType.BYTE)) return;

        e.setCancelled(true); // nie otwieraj normalnego inventory skrzynki
        Player player = e.getPlayer();
        block.setType(Material.AIR); // zuzyj skrzynke

        if (ThreadLocalRandom.current().nextBoolean()) {
            Enchantment ench = GOOD_BOOKS.get(ThreadLocalRandom.current().nextInt(GOOD_BOOKS.size()));
            player.getInventory().addItem(book(ench));
            player.sendMessage(plugin.messages().prefixed("pandora.reward", Map.of("item", ench.getKey().getKey())));
        } else {
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
