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
            "forcestart", "forceend", "gameinfo", "menu", "spectate", "classes", "class", "buyclass",
            "shop", "buyrecipe", "setnpc", "sethologram", "quests", "season", "stats", "setstat",
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
            sender.sendMessage(msg.legacy("&cTen serwer nie jest arena (rola LOBBY)."));
            return;
        }
        if (plugin.games().join(player)) {
            var inst = plugin.games().current();
            player.sendMessage(msg.prefixed("game.join-game",
                    Map.of("instance", inst != null ? inst.world().getName() : "-")));
        } else {
            player.sendMessage(msg.legacy("&cNie mozna dolaczyc (gra w toku lub pelna)."));
        }
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        if (plugin.games() != null) plugin.games().leave(player.getUniqueId());
    }

    private void handleForce(CommandSender sender, boolean start) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) {
            sender.sendMessage(msg.prefixed("general.no-permission", null));
            return;
        }
        if (plugin.games() == null || plugin.games().current() == null) {
            sender.sendMessage(msg.legacy("&cBrak aktywnej instancji (rola LOBBY?)."));
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
                default -> sender.sendMessage(msg.legacy("&cUzycie: /uhc season [start|end]"));
            }
            return;
        }
        if (sender instanceof Player player) plugin.seasonGui().open(player);
        else sender.sendMessage(msg.legacy("&cUzycie: /uhc season <start|end>"));
    }

    private void handleStats(CommandSender sender, String[] args) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) { sender.sendMessage(msg.prefixed("general.no-permission", null)); return; }
        if (args.length < 2) { sender.sendMessage(msg.legacy("&cUzycie: /uhc stats <gracz>")); return; }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(msg.prefixed("admin.player-not-found", Map.of("player", args[1]))); return; }
        PlayerProfile p = plugin.profiles().get(target.getUniqueId());
        if (p == null) { sender.sendMessage(msg.legacy("&cProfil sie laduje.")); return; }
        sender.sendMessage(msg.legacy("&6Statystyki &e" + p.getName() + "&6:"));
        sender.sendMessage(msg.legacy("&7XP: &e" + p.getCredits() + " &8| &7PD: &b" + p.getProgressPoints()
                + " &8| &7Poziom: &6" + p.getLevel()));
        sender.sendMessage(msg.legacy("&7Kille: &e" + p.getKills() + " &8| &7Wygrane: &a" + p.getWins()
                + " &8| &7Klasa: &e" + p.getSelectedClass()));
    }

    private void handleSetStat(CommandSender sender, String[] args) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) { sender.sendMessage(msg.prefixed("general.no-permission", null)); return; }
        if (args.length < 4) { sender.sendMessage(msg.legacy("&cUzycie: /uhc setstat <gracz> <xp|pd|level|kills|wins> <wartosc>")); return; }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(msg.prefixed("admin.player-not-found", Map.of("player", args[1]))); return; }
        PlayerProfile p = plugin.profiles().get(target.getUniqueId());
        if (p == null) { sender.sendMessage(msg.legacy("&cProfil sie laduje.")); return; }
        long value;
        try { value = Long.parseLong(args[3]); } catch (NumberFormatException ex) { sender.sendMessage(msg.legacy("&cNiepoprawna liczba.")); return; }
        switch (args[2].toLowerCase()) {
            case "xp" -> p.setCredits(value);
            case "pd" -> p.setProgressPoints(value);
            case "level" -> p.setLevel((int) value);
            case "kills" -> p.setKills((int) value);
            case "wins" -> p.setWins((int) value);
            default -> { sender.sendMessage(msg.legacy("&cNieznane pole.")); return; }
        }
        plugin.profiles().saveNow(p);
        sender.sendMessage(msg.legacy("&aUstawiono &e" + args[2] + " &adla &e" + p.getName() + " &ana &e" + value));
    }

    private void handleSetNpc(CommandSender sender, String[] args) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) { sender.sendMessage(msg.prefixed("general.no-permission", null)); return; }
        if (!(sender instanceof Player player)) { sender.sendMessage(msg.prefixed("general.players-only", null)); return; }
        if (args.length < 2) { sender.sendMessage(msg.legacy("&cUzycie: /uhc setnpc <mietek|krzysiu|sklepikarz>")); return; }
        String id = args[1].toLowerCase();
        writeLocation("lobby.npcs." + id, player.getLocation(), true);
        plugin.reloadLobbyNpcs();
        sender.sendMessage(msg.prefixed("npc.set", Map.of("id", id)));
    }

    private void handleSetHologram(CommandSender sender, String[] args) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) { sender.sendMessage(msg.prefixed("general.no-permission", null)); return; }
        if (!(sender instanceof Player player)) { sender.sendMessage(msg.prefixed("general.players-only", null)); return; }
        if (args.length < 2) { sender.sendMessage(msg.legacy("&cUzycie: /uhc sethologram <kills|wins|level>")); return; }
        String id = args[1].toLowerCase();
        writeLocation("lobby.leaderboards.holograms." + id, player.getLocation(), false);
        if (plugin.leaderboards() != null) plugin.leaderboards().refresh();
        sender.sendMessage(msg.prefixed("npc.hologram-set", Map.of("id", id)));
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
        if (plugin.spectateGui() == null) { sender.sendMessage(msg.legacy("&cNiedostepne (rola LOBBY).")); return; }
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
        if (args.length < 2) { sender.sendMessage(msg.legacy("&cUzycie: /uhc buyrecipe <id>")); return; }
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) return;
        String id = args[1].toLowerCase();
        if (plugin.shop().owns(p, id)) { player.sendMessage(msg.prefixed("shop.already-owned-recipe", null)); return; }
        long price = plugin.shop().price(id);
        if (plugin.shop().buy(p, id)) {
            player.sendMessage(msg.prefixed("shop.bought-recipe", Map.of(
                    "recipe", plugin.shop().displayName(id),
                    "price", String.valueOf(price),
                    "currency", msg.raw("currency.name"))));
        } else {
            player.sendMessage(msg.prefixed("currency.not-enough", Map.of(
                    "name", msg.raw("currency.name"),
                    "need", String.valueOf(price),
                    "have", String.valueOf(p.getCredits()))));
        }
    }

    private void handleClassList(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (!(sender instanceof Player player)) { sender.sendMessage(msg.prefixed("general.players-only", null)); return; }
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) return;
        sender.sendMessage(msg.legacy("&6Klasy:"));
        for (String id : plugin.classes().ids()) {
            boolean owned = plugin.classes().isUnlocked(p, id);
            boolean sel = id.equals(p.getSelectedClass());
            sender.sendMessage(msg.legacy("&7- &e" + plugin.classes().displayName(id)
                    + " &7(" + plugin.classes().price(id) + " XP) "
                    + (sel ? "&a[wybrana]" : owned ? "&a[posiadana]" : "&c[zablokowana]")));
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
        if (args.length < 2) { sender.sendMessage(msg.legacy("&cUzycie: /uhc buyclass <id>")); return; }
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) return;
        String id = args[1].toLowerCase();
        if (!plugin.classes().exists(id)) { sender.sendMessage(msg.legacy("&cNie ma takiej klasy.")); return; }
        if (plugin.classes().isUnlocked(p, id)) {
            player.sendMessage(msg.prefixed("class.already-owned", null));
            return;
        }
        long price = plugin.classes().price(id);
        if (plugin.classes().buy(p, id)) {
            player.sendMessage(msg.prefixed("class.bought", Map.of(
                    "class", plugin.classes().displayName(id),
                    "price", String.valueOf(price),
                    "currency", msg.raw("currency.name"))));
        } else {
            player.sendMessage(msg.prefixed("currency.not-enough", Map.of(
                    "name", msg.raw("currency.name"),
                    "need", String.valueOf(price),
                    "have", String.valueOf(p.getCredits()))));
        }
    }

    private void handleGameInfo(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (plugin.games() == null) {
            sender.sendMessage(msg.legacy("&7Rola LOBBY — brak instancji gry."));
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

    private void handleBalance(CommandSender sender) {
        MessagesManager msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.prefixed("general.players-only", null));
            return;
        }
        PlayerProfile p = plugin.profiles().get(player.getUniqueId());
        if (p == null) {
            sender.sendMessage(msg.legacy("&cProfil jeszcze sie laduje, sprobuj za chwile."));
            return;
        }
        String currencyName = msg.raw("currency.name");
        player.sendMessage(msg.prefixed("currency.balance", Map.of(
                "name", currencyName,
                "amount", String.valueOf(p.getCredits()))));
        player.sendMessage(msg.prefixed("progress.status", Map.of(
                "level", String.valueOf(p.getLevel()),
                "star", plugin.levels().starSymbol(),
                "current", String.valueOf(p.getProgressPoints()),
                "required", String.valueOf(plugin.levels().requiredForLevel(p.getLevel())))));
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
        sender.sendMessage(msg.prefixed("general.reloaded", null));
    }

    private void handleGive(CommandSender sender, String[] args, boolean currency) {
        MessagesManager msg = plugin.messages();
        if (!sender.hasPermission("ultrahc.admin")) {
            sender.sendMessage(msg.prefixed("general.no-permission", null));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(msg.legacy("&cUzycie: /uhc " + args[0] + " <gracz> <ilosc>"));
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
            sender.sendMessage(msg.legacy("&cNiepoprawna liczba."));
            return;
        }
        PlayerProfile p = plugin.profiles().get(target.getUniqueId());
        if (p == null) {
            sender.sendMessage(msg.legacy("&cProfil gracza jeszcze sie laduje."));
            return;
        }
        if (currency) {
            plugin.currency().add(p, amount);
            sender.sendMessage(msg.prefixed("admin.currency-given",
                    Map.of("amount", String.valueOf(amount), "player", target.getName())));
        } else {
            int gained = plugin.levels().addProgress(p, amount);
            sender.sendMessage(msg.prefixed("admin.pd-given",
                    Map.of("amount", String.valueOf(amount), "player", target.getName())));
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
            sender.sendMessage(msg.legacy("&cBlad resetu sezonu: " + e.getMessage()));
        }
    }
}
