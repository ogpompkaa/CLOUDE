package pl.ultrahc.paper.manager;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.game.GameInstance;
import pl.ultrahc.paper.game.GameState;
import pl.ultrahc.paper.util.NumberUtil;
import pl.ultrahc.paper.util.TimeUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bossbar pokazujacy aktualna faze gry (odliczanie PvP, kurczenie granicy, arenka). */
public class BossBarService {

    private final UltraHcPlugin plugin;
    private final BossBar bar = BossBar.bossBar(net.kyori.adventure.text.Component.empty(),
            1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
    private final Set<UUID> viewers = new HashSet<>();
    private BukkitTask task;

    public BossBarService(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        clearViewers();
    }

    private void tick() {
        GameInstance game = plugin.games() == null ? null : plugin.games().current();
        if (game == null || game.state() != GameState.RUNNING) {
            clearViewers();
            return;
        }
        update(game);
        // Pokaz bossbar wszystkim w swiecie gry.
        for (Player p : game.world().getPlayers()) {
            if (viewers.add(p.getUniqueId())) p.showBossBar(bar);
        }
    }

    private void update(GameInstance game) {
        var cfg = plugin.configManager().raw();
        long elapsed = game.elapsedSeconds();
        int noPvp = cfg.getInt("game.no-pvp-seconds", 600);
        int shrinkStart = cfg.getInt("border.shrink-start-min", 10) * 60;
        int showdown = cfg.getInt("arena-showdown.teleport-min", 45) * 60;

        String text;
        float progress;
        BossBar.Color color;
        if (elapsed < noPvp) {
            long left = noPvp - elapsed;
            text = plugin.messages().raw("bossbar.no-pvp", Map.of("time", TimeUtil.ms(left)));
            progress = clamp((float) left / noPvp);
            color = BossBar.Color.GREEN;
        } else if (elapsed < shrinkStart) {
            long left = shrinkStart - elapsed;
            text = plugin.messages().raw("bossbar.shrink-soon", Map.of("time", TimeUtil.ms(left)));
            progress = clamp((float) left / Math.max(1, shrinkStart - noPvp));
            color = BossBar.Color.YELLOW;
        } else if (elapsed < showdown) {
            double size = game.world().getWorldBorder().getSize();
            text = plugin.messages().raw("bossbar.shrinking", Map.of("size", NumberUtil.oneDecimalComma(size)));
            progress = clamp((float) (size / cfg.getDouble("border.start", 1000)));
            color = BossBar.Color.RED;
        } else {
            text = plugin.messages().raw("bossbar.showdown", Map.of());
            progress = 1.0f;
            color = BossBar.Color.PURPLE;
        }
        bar.name(plugin.messages().legacy(text));
        bar.progress(progress);
        bar.color(color);
    }

    private float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void clearViewers() {
        for (UUID id : viewers) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) p.hideBossBar(bar);
        }
        viewers.clear();
    }
}
