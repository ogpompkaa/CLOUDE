package pl.ultrahc.paper.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.game.GameInstance;
import pl.ultrahc.paper.game.Team;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Czat z ranga (prefiks poziomu/gwiazdki + podium) oraz warstwy jakosciowe:
 * kanaly (spectator pisze tylko do martwych, prefiks druzynowy = czat druzyny),
 * interaktywny nick (hover ze statystykami + klik = /msg), ping przy wzmiance,
 * anty-spam (cooldown + blok powtorzen) i filtr reklam. Wszystko konfigurowalne.
 */
public class ChatListener implements Listener {

    private final UltraHcPlugin plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    // Adresy serwerow / linki (reklama): IP lub domena z popularnym TLD.
    private static final Pattern AD = Pattern.compile(
            "(?i)((\\d{1,3}\\.){3}\\d{1,3}|[a-z0-9-]+\\.(pl|net|com|eu|gg|org|io|xyz|fun|top|club|shop|me))");

    private final Map<UUID, Long> lastTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMsg = new ConcurrentHashMap<>();

    public ChatListener(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    private org.bukkit.configuration.file.FileConfiguration cfg() { return plugin.configManager().raw(); }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player source = e.getPlayer();
        String plain = PLAIN.serialize(e.message());

        // 1) Anty-spam + filtr reklam (przed dostarczeniem).
        if (!source.hasPermission("ultrahc.chat.bypass") && antiSpamBlocks(source, plain)) {
            e.setCancelled(true);
            return;
        }

        GameInstance game = plugin.games() == null ? null : plugin.games().current();
        Team team = (game != null && game.teams() != null) ? game.teams().getTeam(source.getUniqueId()) : null;

        // 2) Kanal SPECTATOR: martwy uczestnik pisze tylko do martwych (nie zdradza pozycji).
        if (team != null && !team.isAlive(source.getUniqueId())) {
            e.viewers().removeIf(a -> a instanceof Player p && !isSpectator(game, p));
            e.renderer((src, dn, msg, viewer) -> channelLine("chat.spectator-format", src, msg, NamedTextColor.GRAY));
            return;
        }

        // 3) Kanal DRUZYNA: wiadomosc z prefiksem (np. @) leci tylko do druzyny.
        String teamPrefix = cfg().getString("chat.team-prefix", "@");
        if (team != null && !teamPrefix.isEmpty() && plain.startsWith(teamPrefix)) {
            String stripped = plain.substring(teamPrefix.length()).trim();
            Component body = Component.text(stripped);
            e.viewers().removeIf(a -> a instanceof Player p && !team.getMembers().contains(p.getUniqueId()));
            e.renderer((src, dn, msg, viewer) -> channelLine("chat.team-format", src, body, NamedTextColor.GREEN));
            return;
        }

        // 4) Kanal GLOBALNY: interaktywny nick (hover + klik) + ping przy wzmiance.
        e.renderer((src, dn, msg, viewer) -> globalLine(src, msg, viewer));
        pingMentions(source, plain);
    }

    // ---------------------------------------------------- formaty kanalow
    private Component globalLine(Player src, Component message, net.kyori.adventure.audience.Audience viewer) {
        Component name = interactiveName(src);
        Component line = name.append(LEGACY.deserialize(plugin.messages().raw("chat.separator"))).append(message);
        // Podswietlenie wzmianki nicku widza.
        if (viewer instanceof Player vp) {
            line = line.replaceText(b -> b.matchLiteral(vp.getName())
                    .replacement(Component.text(vp.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        }
        return line;
    }

    private Component channelLine(String key, Player src, Component message, NamedTextColor bodyColor) {
        // W kanale (Druzyna/Martwi) tylko sam prefiks rangi — kanal ma juz swoja etykiete.
        Component header = LEGACY.deserialize(plugin.messages().raw(key,
                Map.of("prefix", plugin.groups().groupPrefix(src), "name", src.getName())));
        return header.append(message.colorIfAbsent(bodyColor));
    }

    /** Nick wg szablonu (ranga + poziom): hover = statystyki, klik = podpowiedz /msg. */
    private Component interactiveName(Player src) {
        PlayerProfile p = plugin.profiles().get(src.getUniqueId());
        Component hover = LEGACY.deserialize(plugin.messages().raw("chat.hover", Map.of(
                "player", src.getName(),
                "level", String.valueOf(p != null ? p.getLevel() : 0),
                "kills", String.valueOf(p != null ? p.getKills() : 0),
                "wins", String.valueOf(p != null ? p.getWins() : 0))));
        return LEGACY.deserialize(plugin.groups().displayName(src))
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.suggestCommand("/msg " + src.getName() + " "));
    }

    // ------------------------------------------------------- ping wzmianki
    private void pingMentions(Player source, String plain) {
        if (!cfg().getBoolean("chat.mention-ping", true)) return;
        String lower = plain.toLowerCase(Locale.ROOT);
        Sound sound = sound(cfg().getString("chat.ping-sound", "block.note_block.pling"),
                Sound.BLOCK_NOTE_BLOCK_PLING);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player vp : plugin.getServer().getOnlinePlayers()) {
                if (vp.equals(source)) continue;
                if (lower.contains(vp.getName().toLowerCase(Locale.ROOT))) {
                    vp.playSound(vp.getLocation(), sound, 1.0f, 1.4f);
                }
            }
        });
    }

    // --------------------------------------------------- anty-spam / reklamy
    private boolean antiSpamBlocks(Player source, String plain) {
        UUID id = source.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldown = cfg().getInt("chat.cooldown-ms", 1500);
        if (cooldown > 0) {
            Long prev = lastTime.get(id);
            if (prev != null && now - prev < cooldown) {
                source.sendMessage(plugin.messages().prefixed("chat.cooldown", null));
                return true;
            }
        }
        if (cfg().getBoolean("chat.block-repeat", true)) {
            String prev = lastMsg.get(id);
            if (prev != null && prev.equalsIgnoreCase(plain.trim()) && !plain.isBlank()) {
                source.sendMessage(plugin.messages().prefixed("chat.repeat", null));
                return true;
            }
        }
        if (cfg().getBoolean("chat.ad-filter", true) && AD.matcher(plain).find()) {
            source.sendMessage(plugin.messages().prefixed("chat.ad-blocked", null));
            return true;
        }
        lastTime.put(id, now);
        lastMsg.put(id, plain.trim());
        return false;
    }

    // --------------------------------------------------------------- helpers
    /** Czy gracz jest obserwatorem (martwym uczestnikiem) tej gry. */
    private boolean isSpectator(GameInstance game, Player p) {
        if (game == null || game.teams() == null) return false;
        Team t = game.teams().getTeam(p.getUniqueId());
        return t != null && !t.isAlive(p.getUniqueId());
    }

    private Sound sound(String key, Sound def) {
        if (key == null) return def;
        Sound s = Registry.SOUNDS.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
        return s != null ? s : def;
    }
}
