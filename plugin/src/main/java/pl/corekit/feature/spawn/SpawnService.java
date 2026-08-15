package pl.corekit.feature.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.corekit.CoreKitPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Holds the server spawn point in a small, human-editable {@code spawn.yml}.
 *
 * <p>Spawn is a single global location read on every {@code /spawn}, so it lives
 * in memory and is only written to disk when an operator changes it — no
 * database round-trip on the hot path. Writes are rare and tiny, so doing them
 * synchronously on the calling thread is acceptable.
 */
public final class SpawnService {

    private final CoreKitPlugin plugin;
    private final File file;
    private volatile Location spawn;

    public SpawnService(CoreKitPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawn.yml");
    }

    public void load() {
        if (!file.exists()) {
            this.spawn = null;
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String worldName = yaml.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getSLF4JLogger().warn("spawn.yml references unknown world '{}'; ignoring.", worldName);
            this.spawn = null;
            return;
        }
        this.spawn = new Location(
                world,
                yaml.getDouble("x"), yaml.getDouble("y"), yaml.getDouble("z"),
                (float) yaml.getDouble("yaw"), (float) yaml.getDouble("pitch"));
    }

    /** The configured spawn, or the primary world's spawn as a sane fallback. */
    public Location resolve() {
        Location current = this.spawn;
        if (current != null) {
            return current;
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public boolean isSet() {
        return spawn != null;
    }

    public void setSpawn(Location location) {
        this.spawn = location.clone();

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", location.getWorld().getName());
        yaml.set("x", location.getX());
        yaml.set("y", location.getY());
        yaml.set("z", location.getZ());
        yaml.set("yaw", location.getYaw());
        yaml.set("pitch", location.getPitch());
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getSLF4JLogger().error("Failed to save spawn.yml", ex);
        }
    }
}
