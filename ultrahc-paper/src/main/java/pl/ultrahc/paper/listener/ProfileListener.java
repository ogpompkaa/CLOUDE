package pl.ultrahc.paper.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.ultrahc.paper.profile.ProfileService;

/** Laduje profil przy wejsciu i zapisuje przy wyjsciu gracza. */
public class ProfileListener implements Listener {

    private final ProfileService profiles;

    public ProfileListener(ProfileService profiles) {
        this.profiles = profiles;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        profiles.loadAsync(e.getPlayer().getUniqueId(), e.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        profiles.saveAndUnloadAsync(e.getPlayer().getUniqueId());
    }
}
