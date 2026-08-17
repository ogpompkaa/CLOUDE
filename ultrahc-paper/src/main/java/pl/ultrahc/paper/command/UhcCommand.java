package pl.ultrahc.paper.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.config.MessagesManager;
import pl.ultrahc.paper.manager.ShopCurrencyManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Komenda /uhc: info dla gracza + akcje admina, z podpowiedziami (tab-complete). */
public class UhcCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "balance", "reload", "givexp", "givepd", "resetseason", "join", "leave",
            "help", "forcestart", "forceend", "gameinfo", "menu", "spectate", "classes", "class", "buyclass",
            "shop", "buyrecipe", "setnpc", "sethologram", "setlobbyspawn", "quests", "season", "stats", "setstat",
            "admin", "instances");

    private final UltraHcPlugin plugin;

    public UhcCommand(UltraHcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessagesManager msg = plugin.messages();
        if (args.length == 0) {
            sender.sendMessage(msg.prefixed("general.unknown-subcommand", null));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> handleHelp(sender);
            case "balance", "xp" -> handleBalance(sender);
            case "reload" -> handleReload(sender);
            case "givexp" -> handleGive(sender, args, true);
            case "givepd" -> handleGive(sender, args, false);
            case "resetseason" -> handleResetSeason(sender);
            case "join" -> handleJoin(sender);
            case "leave" -> handleLeave(sender);
            case "forcestart" -> handleForce(sender, true);
            case "forceend" -> handleForce(sender, false);
            case "gameinfo" -> handleGameInfo(sender);
            case "menu" -> { if (sender instanceof Player pl) plugin.menu().open(pl); else sender.sendMessage(msg.prefixed("general.players-only", null)); }
            case "spectate" -> handleSpectate(sender);
            case "classes" -> handleClassList(sender);
            case "class" -> handleClassSelect(sender, args);
            case "buyclass" -> handleClassBuy(sender, args);
            case "shop" -> handleShop(sender);
            case "buyrecipe" -> handleBuyRecipe(sender, args);
            case "setnpc" -> handleSetNpc(sender, args);
            case "sethologram" -> handleSetHologram(sender, args);
            case "setlobbyspawn" -> handleSetLobbySpawn(sender);
            case "quests" -> handleQuests(sender);
            case "season" -> handleSeason(sender, args);
            case "stats" -> handleStats(sender, args);
            case "setstat" -> handleSetStat(sender, args);
            case "admin" -> handleAdminPanel(sender);
            case "instances" -> handleInstances(sender);
            default -> sender.sendMessage(msg.prefixed("general.unknown-subcommand", null));
        }
        return true;
    }

    private void handleJoin(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.prefixed("general.players-only", null));
            return;
        }
        if (plugin.games() == null) {
            sender.sendMessage(msg.prefixed("general.arena-only", null));
            return;
        }
        if (plugin.games().join(player)) {
            var inst = plugin.games().current();
            player.sendMessage(msg.prefixed("game.join-game",
                    Map.of("instance", inst != null ? inst.world().getName() : "-")));
            // Lider zabiera online czlonkow party do tej samej instancji.
            if (plugin.party() != null && plugin.party().isLeader(player.getUniqueId())) {
                player.sendMessage(msg.prefixed("party.warp", null));
                for (Player mate : plugin.party().onlineMembers(player.getUniqueId())) {
                    if (mate.equals(player)) continue;
                    if (plugin.games().join(mate)) {
                        mate.sendMessage(msg.prefixed("game.join-game",
                                Map.of("instance", inst != null ? inst.world().getName() : "-")));
                    }
                }
            }
        } else {
            player.sendMessage(msg.prefixed("game.join-failed", null));
        }
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        if (plugin.games() != null) plugin.games().leave(player.getUniqueId());
        // Na arenie: wyjscie z gry = powrot na hub (jesli skonfigurowany transfer).
        if (plugin.arenaLobbyListener() != null) plugin.arenaLobbyListener().connectHub(player);
    }

    private void handleForce(CommandSender sender, boolean start) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) {
            sender.sendMessage(msg.prefixed("general.no-permission", null));
            return;
        }
        if (plugin.games() == null || plugin.games().current() == null) {
            sender.sendMessage(msg.prefixed("general.no-instance", null));
            return;
        }
        if (start) {
            plugin.games().current().forceStart();
            sender.sendMessage(msg.prefixed("admin.force-start", null));
        } else {
            plugin.games().current().forceEnd();
            sender.sendMessage(msg.prefixed("admin.force-end", null));
        }
    }

    private void handleQuests(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (!(sender instanceof Player player)) { sender.sendMessage(msg.prefixed("general.players-only", null)); return; }
        plugin.questGui().open(player);
    }

    private void handleSeason(CommandSender sender, String[] args) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) { sender.sendMessage(msg.prefixed("general.no-permission", null)); return; }
        if (args.length >= 2) {
            switch (args[1].toLowerCase()) {
                case "start" -> plugin.season().startSeason(sender);
                case "end" -> plugin.season().endSeason(sender);
                default -> usage(sender, "/uhc season [start|end]");
            }
            return;
        }
        if (sender instanceof Player player) plugin.seasonGui().open(player);
        else usage(sender, "/uhc season <start|end>");
    }

    private void handleStats(CommandSender sender, String[] args) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) { sender.sendMessage(msg.prefixed("general.no-permission", null)); return; }
        if (args.length < 2) { usage(sender, "/uhc stats <gracz>"); return; }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(msg.prefixed("admin.player-not-found", Map.of("player", args[1]))); return; }
        PlayerProfile p = plugin.profiles().get(target.getUniqueId());
        if (p == null) { loading(sender); return; }
        sender.sendMessage(msg.prefixed("admin.stats-header", Map.of("player", p.getName())));
        sender.sendMessage(msg.prefixed("admin.stats-line1", Map.of(
                "xp", num(p.getCredits()), "pd", num(p.getProgressPoints()),
                "level", String.valueOf(p.getLevel()))));
        sender.sendMessage(msg.prefixed("admin.stats-line2", Map.of(
                "kills", String.valueOf(p.getKills()), "wins", String.valueOf(p.getWins()),
                "class", plugin.classes().displayName(p.getSelectedClass()))));
    }

    private void handleSetStat(CommandSender sender, String[] args) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) { sender.sendMessage(msg.prefixed("general.no-permission", null)); return; }
        if (args.length < 4) { usage(sender, "/uhc setstat <gracz> <xp|pd|level|kills|wins> <wartosc>"); return; }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(msg.prefixed("admin.player-not-found", Map.of("player", args[1]))); return; }
        PlayerProfile p = plugin.profiles().get(target.getUniqueId());
        if (p == null) { loading(sender); return; }
        long value;
        try { value = Long.parseLong(args[3]); } catch (NumberFormatException ex) { sender.sendMessage(msg.prefixed("general.invalid-number", null)); return; }
        switch (args[2].toLowerCase()) {
            case "xp" -> p.setCredits(value);
            case "pd" -> p.setProgressPoints(value);
            case "level" -> p.setLevel((int) value);
            case "kills" -> p.setKills((int) value);
            case "wins" -> p.setWins((int) value);
            default -> { sender.sendMessage(msg.prefixed("admin.unknown-field", null)); return; }
        }
        plugin.profiles().saveNow(p);
        sender.sendMessage(msg.prefixed("admin.stat-set", Map.of(
                "field", args[2], "player", p.getName(), "value", String.valueOf(value))));
    }

    private void handleSetNpc(CommandSender sender, String[] args) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) { sender.sendMessage(msg.prefixed("general.no-permission", null)); return; }
        if (!(sender instanceof Player player)) { sender.sendMessage(msg.prefixed("general.players-only", null)); return; }
        if (args.length < 2) { usage(sender, "/uhc setnpc <mietek|krzysiu|sklepikarz>"); return; }
        String id = args[1].toLowerCase();
        writeLocation("lobby.npcs." + id, player.getLocation(), true);
        plugin.reloadLobbyNpcs();
        sender.sendMessage(msg.prefixed("npc.set", Map.of("id", id)));
    }

    private void handleSetHologram(CommandSender sender, String[] args) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) { sender.sendMessage(msg.prefixed("general.no-permission", null)); return; }
        if (!(sender instanceof Player player)) { sender.sendMessage(msg.prefixed("general.players-only", null)); return; }
        if (args.length < 2) { usage(sender, "/uhc sethologram <kills|wins|level>"); return; }
        String id = args[1].toLowerCase();
        writeLocation("lobby.leaderboards.holograms." + id, player.getLocation(), false);
        if (plugin.leaderboards() != null) plugin.leaderboards().refresh();
        sender.sendMessage(msg.prefixed("npc.hologram-set", Map.of("id", id)));
    }

    private void handleSetLobbySpawn(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) { sender.sendMessage(msg.prefixed("general.no-permission", null)); return; }
        if (!(sender instanceof Player player)) { sender.sendMessage(msg.prefixed("general.players-only", null)); return; }
        writeLocation("arena.lobby.spawn", player.getLocation(), true);
        sender.sendMessage(msg.prefixed("arena.lobby-spawn-set",
                Map.of("world", player.getWorld().getName())));
    }

    private void writeLocation(String path, org.bukkit.Location loc, boolean withRotation) {
        var cfg = plugin.getConfig();
        cfg.set(path + ".world", loc.getWorld().getName());
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        if (withRotation) {
            cfg.set(path + ".yaw", (double) loc.getYaw());
            cfg.set(path + ".pitch", (double) loc.getPitch());
        }
        plugin.saveConfig();
    }

    private void handleAdminPanel(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) { sender.sendMessage(msg.prefixed("general.no-permission", null)); return; }
        if (sender instanceof Player player) plugin.adminGui().open(player);
        else sender.sendMessage(msg.prefixed("general.players-only", null));
    }

    private void handleInstances(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) { sender.sendMessage(msg.prefixed("general.no-permission", null)); return; }
        if (sender instanceof Player player) plugin.instanceAdminGui().open(player);
        else sender.sendMessage(msg.prefixed("general.players-only", null));
    }

    private void handleSpectate(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (!(sender instanceof Player player)) { sender.sendMessage(msg.prefixed("general.players-only", null)); return; }
        if (plugin.spectateGui() == null) { sender.sendMessage(msg.prefixed("general.feature-lobby-only", null)); return; }
        plugin.spectateGui().open(player);
    }

    private void handleShop(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (!(sender instanceof Player player)) { sender.sendMessage(msg.prefixed("general.players-only", null)); return; }
        plugin.shopGui().open(player);
    }

    private void handleBuyRecipe(CommandSender sender, String[] args) {
        MessagesManager msg = plugin.messages();
        if (!(sender instanceof Player player)) { sender.sendMessage(msg.prefixed("general.players-only", null)); return; }
        if (args.length < 2) { usage(sender, "/uhc buyrecipe <id>"); return; }
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) return;
        String id = args[1].toLowerCase();
        if (plugin.shop().owns(p, id)) { player.sendMessage(msg.prefixed("shop.already-owned-recipe", null)); return; }
        long price = plugin.shop().price(id);
        if (plugin.shop().buy(p, id)) {
            player.sendMessage(msg.prefixed("shop.bought-recipe", Map.of(
                    "recipe", plugin.shop().displayName(id),
                    "price", num(price),
                    "currency", msg.raw("currency.name"))));
        } else {
            player.sendMessage(msg.prefixed("currency.not-enough", Map.of(
                    "name", msg.raw("currency.name"),
                    "need", num(price),
                    "have", num(p.getCredits()))));
        }
    }

    private void handleClassList(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (!(sender instanceof Player player)) { sender.sendMessage(msg.prefixed("general.players-only", null)); return; }
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) return;
        sender.sendMessage(msg.prefixed("class.list-header", null));
        for (String id : plugin.classes().ids()) {
            boolean owned = plugin.classes().isUnlocked(p, id);
            boolean sel = id.equals(p.getSelectedClass());
            String status = msg.raw(sel ? "class.tag-selected" : owned ? "class.tag-owned" : "class.tag-locked");
            sender.sendMessage(msg.prefixed("class.list-entry", Map.of(
                    "class", plugin.classes().displayName(id),
                    "price", String.valueOf(plugin.classes().price(id)),
                    "status", status)));
        }
    }

    private void handleClassSelect(CommandSender sender, String[] args) {
        MessagesManager msg = plugin.messages();
        if (!(sender instanceof Player player)) { sender.sendMessage(msg.prefixed("general.players-only", null)); return; }
        if (args.length < 2) { plugin.classGui().open(player); return; } // bez argumentu -> GUI
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) return;
        String id = args[1].toLowerCase();
        if (plugin.classes().select(p, id)) {
            player.sendMessage(msg.prefixed("class.selected", Map.of("class", plugin.classes().displayName(id))));
        } else {
            player.sendMessage(msg.prefixed("class.locked", null));
        }
    }

    private void handleClassBuy(CommandSender sender, String[] args) {
        MessagesManager msg = plugin.messages();
        if (!(sender instanceof Player player)) { sender.sendMessage(msg.prefixed("general.players-only", null)); return; }
        if (args.length < 2) { usage(sender, "/uhc buyclass <id>"); return; }
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) return;
        String id = args[1].toLowerCase();
        if (!plugin.classes().exists(id)) { sender.sendMessage(msg.prefixed("class.not-found", null)); return; }
        if (plugin.classes().isUnlocked(p, id)) {
            player.sendMessage(msg.prefixed("class.already-owned", null));
            return;
        }
        long price = plugin.classes().price(id);
        if (plugin.classes().buy(p, id)) {
            player.sendMessage(msg.prefixed("class.bought", Map.of(
                    "class", plugin.classes().displayName(id),
                    "price", num(price),
                    "currency", msg.raw("currency.name"))));
        } else {
            player.sendMessage(msg.prefixed("currency.not-enough", Map.of(
                    "name", msg.raw("currency.name"),
                    "need", num(price),
                    "have", num(p.getCredits()))));
        }
    }

    private void handleGameInfo(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (plugin.games() == null) {
            sender.sendMessage(msg.prefixed("general.lobby-role", null));
            return;
        }
        for (String line : plugin.games().describeState()) {
            sender.sendMessage(msg.legacy("&7" + line));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new ArrayList<>());
        }
        if (args.length == 2) {
            List<String> pool = switch (args[0].toLowerCase()) {
                case "class", "buyclass" -> plugin.classes().ids();
                case "buyrecipe" -> plugin.shop().recipeIds();
                case "givexp", "givepd", "stats", "setstat" -> onlineNames();
                case "setnpc" -> List.of("mietek", "krzysiu", "sklepikarz");
                case "sethologram" -> List.of("kills", "wins", "level");
                case "season" -> List.of("start", "end");
                default -> List.of();
            };
            return StringUtil.copyPartialMatches(args[1], pool, new ArrayList<>());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setstat")) {
            return StringUtil.copyPartialMatches(args[2], List.of("xp", "pd", "level", "kills", "wins"), new ArrayList<>());
        }
        return out;
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) names.add(p.getName());
        return names;
    }

    private void usage(CommandSender sender, String syntax) {
        sender.sendMessage(plugin.messages().prefixed("general.usage", Map.of("usage", syntax)));
    }

    private void loading(CommandSender sender) {
        sender.sendMessage(plugin.messages().prefixed("general.profile-loading", null));
    }

    /** Liczba z separatorem tysiecy (spojne formatowanie walut). */
    private String num(long v) {
        return pl.ultrahc.paper.util.NumberUtil.grouped(v);
    }

    private void handleHelp(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        sender.sendMessage(msg.legacy(msg.raw("help.header")));
        for (String line : msg.rawList("help.lines")) sender.sendMessage(msg.legacy(line));
        if (sender.hasPermission("ultrahc.admin")) sender.sendMessage(msg.legacy(msg.raw("help.admin")));
    }

    private void handleBalance(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.prefixed("general.players-only", null));
            return;
        }
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) {
            loading(sender);
            return;
        }
        String currencyName = msg.raw("currency.name");
        player.sendMessage(msg.prefixed("currency.balance", Map.of(
                "name", currencyName,
                "amount", pl.ultrahc.paper.util.NumberUtil.grouped(p.getCredits()))));
        player.sendMessage(msg.prefixed("progress.status", Map.of(
                "level", String.valueOf(p.getLevel()),
                "star", plugin.levels().starSymbol(),
                "current", num(p.getProgressPoints()),
                "required", num(plugin.levels().requiredForLevel(p.getLevel())))));
    }

    private void handleReload(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) {
            sender.sendMessage(msg.prefixed("general.no-permission", null));
            return;
        }
        plugin.configManager().load();
        plugin.messages().load();
        plugin.levels().reload();
        if (plugin.groups() != null) plugin.groups().reload();
        if (plugin.quests() != null) plugin.quests().loadDefs();        // odswiez definicje questow
        // WAZNE: reloadConfig() podmienia obiekt configu — odswiez referencje w Feedback.
        pl.ultrahc.paper.util.Feedback.configure(plugin.configManager().raw());
        // Zaloguj ewentualne ostrzezenia walidatora po zmianie configu.
        for (String warn : pl.ultrahc.paper.config.ConfigValidator.validate(plugin.configManager().raw())) {
            plugin.getLogger().warning("[UltraHC] Config: " + warn);
        }
        sender.sendMessage(msg.prefixed("general.reloaded", null));
    }

    private void handleGive(CommandSender sender, String[] args, boolean currency) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) {
            sender.sendMessage(msg.prefixed("general.no-permission", null));
            return;
        }
        if (args.length < 3) {
            usage(sender, "/uhc " + args[0] + " <gracz> <ilosc>");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(msg.prefixed("admin.player-not-found", Map.of("player", args[1])));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(msg.prefixed("general.invalid-number", null));
            return;
        }
        PlayerProfile p = plugin.profiles().get(target.getUniqueId());
        if (p == null) {
            loading(sender);
            return;
        }
        if (currency) {
            plugin.currency().add(p, amount);
            sender.sendMessage(msg.prefixed("admin.currency-given",
                    Map.of("amount", num(amount), "player", target.getName())));
        } else {
            int gained = plugin.levels().addProgress(p, amount);
            sender.sendMessage(msg.prefixed("admin.pd-given",
                    Map.of("amount", num(amount), "player", target.getName())));
            if (gained > 0) {
                target.sendMessage(msg.prefixed("progress.level-up",
                        Map.of("level", String.valueOf(p.getLevel()), "star", plugin.levels().starSymbol())));
            }
        }
        plugin.profiles().saveNow(p);
    }

    private void handleResetSeason(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) {
            sender.sendMessage(msg.prefixed("general.no-permission", null));
            return;
        }
        try {
            plugin.profiles().storage().resetSeason();
            sender.sendMessage(msg.prefixed("admin.season-reset", null));
        } catch (Exception e) {
            sender.sendMessage(msg.prefixed("admin.season-error", Map.of("error", String.valueOf(e.getMessage()))));
        }
    }
}
