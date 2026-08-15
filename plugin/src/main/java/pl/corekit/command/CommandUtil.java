package pl.corekit.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Small shared helpers for the Brigadier command handlers. */
public final class CommandUtil {

    private CommandUtil() {
    }

    /**
     * Resolves a single {@link Player} from a {@code ArgumentTypes.player()}
     * argument. Throws the native Brigadier "no player found" error if the
     * selector matched nobody — Paper surfaces that message to the sender.
     */
    public static Player resolveTarget(CommandContext<CommandSourceStack> context, String argument)
            throws CommandSyntaxException {
        PlayerSelectorArgumentResolver resolver =
                context.getArgument(argument, PlayerSelectorArgumentResolver.class);
        return resolver.resolve(context.getSource()).get(0);
    }

    /** @return the command sender as a {@link Player}, or {@code null} if it is the console. */
    public static Player asPlayer(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        return sender instanceof Player player ? player : null;
    }
}
