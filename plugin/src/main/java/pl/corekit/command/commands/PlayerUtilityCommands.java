package pl.corekit.command.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.corekit.CoreKitPlugin;
import pl.corekit.command.CommandUtil;
import pl.corekit.command.CoreKitCommand;
import pl.corekit.feature.god.GodService;
import pl.corekit.lang.MessageService;

/**
 * Basic self-care / staff utilities: {@code /heal}, {@code /feed}, {@code /fly},
 * {@code /god}. Each runs on the sender by default and, with a separate
 * {@code .others} permission, on a target player. The target is always notified
 * so effects applied to them are never silent.
 */
public final class PlayerUtilityCommands implements CoreKitCommand {

    private final CoreKitPlugin plugin;
    private final MessageService messages;
    private final GodService god;

    public PlayerUtilityCommands(CoreKitPlugin plugin, MessageService messages, GodService god) {
        this.plugin = plugin;
        this.messages = messages;
        this.god = god;
    }

    @Override
    public void register(Commands registrar) {
        registrar.register(plugin.getPluginMeta(), heal(), "Restore health and hunger", java.util.List.of());
        registrar.register(plugin.getPluginMeta(), feed(), "Restore hunger", java.util.List.of());
        registrar.register(plugin.getPluginMeta(), fly(), "Toggle flight", java.util.List.of());
        registrar.register(plugin.getPluginMeta(), godMode(), "Toggle damage immunity", java.util.List.of());
    }

    // ------------------------------------------------------------------ heal

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> heal() {
        return Commands.literal("heal")
                .requires(s -> s.getSender().hasPermission("corekit.heal"))
                .executes(ctx -> selfAction(ctx, target -> {
                    applyHeal(target);
                    messages.send(target, "heal.self");
                }))
                .then(Commands.argument("target", ArgumentTypes.player())
                        .requires(s -> s.getSender().hasPermission("corekit.heal.others"))
                        .executes(ctx -> targetAction(ctx, "heal", target -> applyHeal(target))))
                .build();
    }

    private void applyHeal(Player target) {
        AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        target.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        target.setFoodLevel(20);
        target.setSaturation(20.0f);
        target.setFireTicks(0);
        target.setRemainingAir(target.getMaximumAir());
    }

    // ------------------------------------------------------------------ feed

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> feed() {
        return Commands.literal("feed")
                .requires(s -> s.getSender().hasPermission("corekit.feed"))
                .executes(ctx -> selfAction(ctx, target -> {
                    applyFeed(target);
                    messages.send(target, "feed.self");
                }))
                .then(Commands.argument("target", ArgumentTypes.player())
                        .requires(s -> s.getSender().hasPermission("corekit.feed.others"))
                        .executes(ctx -> targetAction(ctx, "feed", target -> applyFeed(target))))
                .build();
    }

    private void applyFeed(Player target) {
        target.setFoodLevel(20);
        target.setSaturation(20.0f);
        target.setExhaustion(0.0f);
    }

    // ------------------------------------------------------------------- fly

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> fly() {
        return Commands.literal("fly")
                .requires(s -> s.getSender().hasPermission("corekit.fly"))
                .executes(ctx -> {
                    Player self = CommandUtil.asPlayer(ctx);
                    if (self == null) {
                        messages.send(ctx.getSource().getSender(), "players-only");
                        return 0;
                    }
                    boolean enabled = toggleFly(self);
                    messages.send(self, enabled ? "fly.enabled" : "fly.disabled");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                        .requires(s -> s.getSender().hasPermission("corekit.fly.others"))
                        .executes(this::flyOther))
                .build();
    }

    private int flyOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player target = CommandUtil.resolveTarget(ctx, "target");
        CommandSender actor = ctx.getSource().getSender();
        boolean enabled = toggleFly(target);

        messages.send(actor, enabled ? "fly.other-enabled" : "fly.other-disabled",
                MessageService.placeholder("target", target.getName()));
        if (!target.equals(actor)) {
            messages.send(target, enabled ? "fly.notify-enabled" : "fly.notify-disabled",
                    MessageService.placeholder("actor", actor.getName()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private boolean toggleFly(Player target) {
        boolean enable = !target.getAllowFlight();
        target.setAllowFlight(enable);
        if (!enable) {
            target.setFlying(false);
        }
        return enable;
    }

    // ------------------------------------------------------------------- god

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> godMode() {
        return Commands.literal("god")
                .requires(s -> s.getSender().hasPermission("corekit.god"))
                .executes(ctx -> {
                    Player self = CommandUtil.asPlayer(ctx);
                    if (self == null) {
                        messages.send(ctx.getSource().getSender(), "players-only");
                        return 0;
                    }
                    boolean enabled = god.toggle(self.getUniqueId());
                    messages.send(self, enabled ? "god.enabled" : "god.disabled");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("target", ArgumentTypes.player())
                        .requires(s -> s.getSender().hasPermission("corekit.god.others"))
                        .executes(this::godOther))
                .build();
    }

    private int godOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player target = CommandUtil.resolveTarget(ctx, "target");
        CommandSender actor = ctx.getSource().getSender();
        boolean enabled = god.toggle(target.getUniqueId());

        messages.send(actor, enabled ? "god.other-enabled" : "god.other-disabled",
                MessageService.placeholder("target", target.getName()));
        if (!target.equals(actor)) {
            messages.send(target, enabled ? "god.enabled" : "god.disabled");
        }
        return Command.SINGLE_SUCCESS;
    }

    // --------------------------------------------------------------- helpers

    /** Runs an action on the sender (must be a player) and reports player-only misuse. */
    private int selfAction(CommandContext<CommandSourceStack> ctx,
                           java.util.function.Consumer<Player> action) {
        Player self = CommandUtil.asPlayer(ctx);
        if (self == null) {
            messages.send(ctx.getSource().getSender(), "players-only");
            return 0;
        }
        action.accept(self);
        return Command.SINGLE_SUCCESS;
    }

    /** Runs an action on a target, messaging both actor and (if different) target. */
    private int targetAction(CommandContext<CommandSourceStack> ctx, String key,
                             java.util.function.Consumer<Player> action) throws CommandSyntaxException {
        Player target = CommandUtil.resolveTarget(ctx, "target");
        CommandSender actor = ctx.getSource().getSender();
        action.accept(target);

        messages.send(actor, key + ".other", MessageService.placeholder("target", target.getName()));
        if (!target.equals(actor)) {
            messages.send(target, key + ".notify", MessageService.placeholder("actor", actor.getName()));
        }
        return Command.SINGLE_SUCCESS;
    }
}
