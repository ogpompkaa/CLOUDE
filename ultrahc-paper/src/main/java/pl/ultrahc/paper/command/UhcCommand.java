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
            default -> sender.sendMessage(msg.prefixed("general.unknown-subcommand", null));
        }
        return true;
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
