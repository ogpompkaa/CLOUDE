package pl.ultrahc.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import pl.ultrahc.common.instances.InstanceInfo;
import pl.ultrahc.common.instances.InstanceRegistry;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** /play — znajduje dolaczalna arene w rejestrze i przenosi na nia gracza. */
public class PlayCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final Supplier<InstanceRegistry> registry;
    private final Supplier<Integer> teamSize;
    private final Supplier<Long> staleMillis;
    private final Logger logger;

    public PlayCommand(ProxyServer proxy, Supplier<InstanceRegistry> registry,
                       Supplier<Integer> teamSize, Supplier<Long> staleMillis, Logger logger) {
        this.proxy = proxy;
        this.registry = registry;
        this.teamSize = teamSize;
        this.staleMillis = staleMillis;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Tylko dla graczy.", NamedTextColor.RED));
            return;
        }
        InstanceRegistry reg = registry.get();
        if (reg == null) {
            player.sendMessage(Component.text("Rejestr instancji niedostepny.", NamedTextColor.RED));
            return;
        }
        try {
            List<InstanceInfo> joinable = reg.listJoinable(teamSize.get(), staleMillis.get());
            if (joinable.isEmpty()) {
                player.sendMessage(Component.text("Brak dostepnych aren — sprobuj za chwile.", NamedTextColor.YELLOW));
                return;
            }
            InstanceInfo target = joinable.get(0); // najwiecej graczy = najszybszy start
            Optional<RegisteredServer> server = proxy.getServer(target.id());
            if (server.isEmpty()) {
                player.sendMessage(Component.text("Arena '" + target.id() + "' nie jest zarejestrowana w proxy.", NamedTextColor.RED));
                logger.warn("[UltraHC] Instancja {} nie ma odpowiadajacego serwera w velocity.toml.", target.id());
                return;
            }
            player.sendMessage(Component.text("Laczenie z areną " + target.id() + "...", NamedTextColor.GREEN));
            player.createConnectionRequest(server.get()).fireAndForget();
        } catch (Exception ex) {
            player.sendMessage(Component.text("Blad routingu: " + ex.getMessage(), NamedTextColor.RED));
        }
    }
}
