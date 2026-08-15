package pl.ultrahc.paper.listener;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.game.GameInstance;
import pl.ultrahc.paper.game.Team;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Umiejetnosci pasywne klas wyzwalane zdarzeniami: Snajper (regen po trafieniu),
 * Straznik (spowolnienie+odpornosc po ciosie), Wedkarz (ksiazki z lowienia),
 * Kowal (przepalone rudy), Szef Kuchni (przepieczone mieso).
 * Ruchowe/wodne pasywki (Yeti, Wedkarz-woda) sa w AbilityScheduler.
 */
public class ClassAbilityListener implements Listener {

    private final UltraHcPlugin plugin;

    public ClassAbilityListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    // --------------------------------------------------- Snajper / Straznik
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(e);
        if (attacker == null || !areEnemies(attacker, victim)) return;

        var cfg = plugin.configManager().raw();
        // Snajper: po trafieniu przeciwnika dostaje Regeneracje I.
        if ("sniper".equals(classOf(attacker))) {
            int sec = cfg.getInt("classes.sniper.ability.regen-seconds", 2);
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, sec * 20, 0));
        }
        // Straznik: po otrzymaniu ciosu dostaje Spowolnienie I + Odpornosc.
        if ("guard".equals(classOf(victim))) {
            int sec = cfg.getInt("classes.guard.ability.slowness-seconds", 3);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, sec * 20, 0));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, sec * 20, 0));
        }
    }

    // ------------------------------------------------------------- Wedkarz
    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!"fisher".equals(classOf(e.getPlayer()))) return;
        if (!(e.getCaught() instanceof Item caught)) return;

        var cfg = plugin.configManager().raw();
        if (roll(cfg.getDouble("classes.fisher.ability.book-protection-chance", 1.0))) {
            caught.setItemStack(book(Enchantment.PROTECTION, 1));
        } else if (roll(cfg.getDouble("classes.fisher.ability.book-sharpness-chance", 0.65))) {
            caught.setItemStack(book(Enchantment.SHARPNESS, 1));
        }
    }

    // --------------------------------------------------------------- Kowal
    @EventHandler(ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent e) {
        if (!"smith".equals(classOf(e.getPlayer()))) return;
        for (Item item : e.getItems()) {
            ItemStack stack = item.getItemStack();
            if (stack.getType() == Material.RAW_IRON) stack.setType(Material.IRON_INGOT);
            else if (stack.getType() == Material.RAW_GOLD) stack.setType(Material.GOLD_INGOT);
            item.setItemStack(stack);
        }
    }

    // ---------------------------------------------------------- Szef Kuchni
    @EventHandler(ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null || !"chef".equals(classOf(killer))) return;
        for (ItemStack drop : e.getDrops()) {
            Material cooked = COOKED.get(drop.getType());
            if (cooked != null) drop.setType(cooked);
        }
    }

    private static final Map<Material, Material> COOKED = Map.of(
            Material.BEEF, Material.COOKED_BEEF,
            Material.PORKCHOP, Material.COOKED_PORKCHOP,
            Material.CHICKEN, Material.COOKED_CHICKEN,
            Material.MUTTON, Material.COOKED_MUTTON,
            Material.RABBIT, Material.COOKED_RABBIT,
            Material.COD, Material.COOKED_COD,
            Material.SALMON, Material.COOKED_SALMON);

    // --------------------------------------------------------------- helpers
    private ItemStack book(Enchantment ench, int level) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
        meta.addStoredEnchant(ench, level, true);
        item.setItemMeta(meta);
        return item;
    }

    private String classOf(Player player) {
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        return p == null ? "civil" : p.getSelectedClass();
    }

    private boolean areEnemies(Player a, Player b) {
        GameInstance game = plugin.games() == null ? null : plugin.games().current();
        if (game == null || game.teams() == null) return false;
        Team ta = game.teams().getTeam(a.getUniqueId());
        Team tb = game.teams().getTeam(b.getUniqueId());
        return ta != null && tb != null && ta != tb;
    }

    private Player resolveAttacker(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) return p;
        if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private boolean roll(double chancePercent) {
        return chancePercent > 0 && ThreadLocalRandom.current().nextDouble() * 100.0 < chancePercent;
    }
}
