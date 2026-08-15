package pl.corekit.bootstrap;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import org.jetbrains.annotations.NotNull;

/**
 * Paper plugin bootstrap. Runs before worlds load and before the plugin is
 * enabled — the correct place for registry work (custom datapack entries,
 * biome/enchantment registration, feature flags) that must exist prior to
 * world generation.
 *
 * <p>Intentionally empty for now: the foundation has no early-registration
 * needs yet, but wiring the bootstrap up front means such work has a home
 * without another restructuring later.
 */
public final class CoreKitBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        // Reserved for pre-world registration. Keep this cheap and side-effect free.
    }
}
