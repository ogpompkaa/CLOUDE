package pl.ultrahc.paper.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.ultrahc.common.model.PlayerProfile;
import pl.ultrahc.paper.UltraHcPlugin;
import pl.ultrahc.paper.config.MessagesManager;
import pl.ultrahc.paper.manager.ShopCurrencyManager;

import java.util.Map;

/** Komenda /uhc: info dla gracza + podstawowe akcje admina (szkielet). */
public class UhcCommand implements CommandExecutor {

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
            case "classes" -> handleClassList(sender);
            case "class" -> handleClassSelect(sender, args);
            case "buyclass" -> handleClassBuy(sender, args);
            case "shop" -> handleShop(sender);
            case "buyrecipe" -> handleBuyRecipe(sender, args);
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
        if (args.length < 2) { sender.sendMessage(msg.legacy("&cUzycie: /uhc class <id>")); return; }
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
