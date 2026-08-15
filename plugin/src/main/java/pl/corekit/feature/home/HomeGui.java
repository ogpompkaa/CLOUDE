package pl.corekit.feature.home;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.corekit.CoreKitPlugin;
import pl.corekit.feature.feedback.FeedbackService;
import pl.corekit.feature.teleport.TeleportService;
import pl.corekit.gui.Icons;
import pl.corekit.gui.Menu;
import pl.corekit.lang.MessageService;
import pl.corekit.storage.Home;
import pl.corekit.storage.HomeRepository;

import java.util.List;

/**
 * GUI front-end for homes.
 *
 * <p>{@code /homes} opens a chest menu of the player's homes: left-click a home
 * to teleport, right-click to delete. Deletion never happens on a single click —
 * it opens a dedicated confirmation menu (Confirm / Cancel), and only Confirm
 * performs the async delete. After a delete the list is re-queried and reopened
 * so the menu always reflects live state.
 */
public final class HomeGui {

    private final CoreKitPlugin plugin;
    private final FeedbackService feedback;
    private final MessageService messages;
    private final HomeService homes;
    private final HomeRepository repository;
    private final TeleportService teleport;

    public HomeGui(CoreKitPlugin plugin, FeedbackService feedback,
                   HomeService homes, TeleportService teleport) {
        this.plugin = plugin;
        this.feedback = feedback;
        this.messages = feedback.messages();
        this.homes = homes;
        this.repository = homes.repository();
        this.teleport = teleport;
    }

    /** Fetches the player's homes off-thread, then opens the menu on the main thread. */
    public void open(Player player) {
        repository.findAll(player.getUniqueId()).thenAccept(list ->
                plugin.database().sync(() -> showHomes(player, list))
        ).exceptionally(throwable -> {
            plugin.getSLF4JLogger().warn("Failed to open homes GUI for {}", player.getName(), throwable);
            plugin.database().sync(() -> feedback.error(player, "command.error"));
            return null;
        });
    }

    private void showHomes(Player player, List<Home> list) {
        if (list.isEmpty()) {
            player.closeInventory();
            feedback.error(player, "home.list-empty");
            return;
        }

        int rows = Math.min(6, Math.max(1, (list.size() + 8) / 9));
        Menu menu = new Menu(messages.render("gui.homes.title"), rows * 9);

        int slot = 0;
        for (Home home : list) {
            if (slot >= rows * 9) {
                break;
            }
            ItemStack icon = Icons.of(Material.RED_BED,
                    messages.render("gui.homes.entry-name", MessageService.placeholder("name", home.name())),
                    List.of(
                            messages.render("gui.homes.entry-tp"),
                            messages.render("gui.homes.entry-del")));
            menu.setButton(slot++, icon, event -> {
                if (event.isRightClick()) {
                    later(() -> showConfirm(player, home.name()));
                } else {
                    teleportTo(player, home);
                }
            });
        }
        menu.open(player);
    }

    private void teleportTo(Player player, Home home) {
        player.closeInventory();
        Location location = home.toLocation();
        if (location == null) {
            feedback.error(player, "home.world-missing",
                    MessageService.placeholder("name", home.name()));
            return;
        }
        teleport.request(player, location, home.name());
    }

    private void showConfirm(Player player, String name) {
        Menu menu = new Menu(
                messages.render("gui.confirm.title", MessageService.placeholder("name", name)), 27);

        // Filler glass around the choice for a cleaner look.
        ItemStack filler = Icons.of(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int i = 0; i < 27; i++) {
            menu.setButton(i, filler, null);
        }

        menu.setButton(13, Icons.of(Material.RED_BED,
                messages.render("gui.confirm.info-name", MessageService.placeholder("name", name)),
                List.of(messages.render("gui.confirm.info-lore"))), null);

        menu.setButton(11, Icons.of(Material.LIME_WOOL,
                messages.render("gui.confirm.yes"),
                List.of(messages.render("gui.confirm.yes-lore"))),
                event -> confirmDelete(player, name));

        menu.setButton(15, Icons.of(Material.RED_WOOL,
                messages.render("gui.confirm.no"),
                List.of(messages.render("gui.confirm.no-lore"))),
                event -> later(() -> open(player)));

        menu.open(player);
    }

    private void confirmDelete(Player player, String name) {
        repository.delete(player.getUniqueId(), name).thenAccept(removed ->
                plugin.database().sync(() -> {
                    if (removed) {
                        homes.forgetName(player.getUniqueId(), name);
                        feedback.success(player, "home.deleted",
                                MessageService.placeholder("name", name));
                    } else {
                        feedback.error(player, "home.not-found",
                                MessageService.placeholder("name", name));
                    }
                    open(player); // refresh the list (closes the menu if now empty)
                })
        ).exceptionally(throwable -> {
            plugin.getSLF4JLogger().warn("Failed to delete home for {}", player.getName(), throwable);
            plugin.database().sync(() -> feedback.error(player, "command.error"));
            return null;
        });
    }

    /**
     * Defers opening an inventory to the next tick. Opening a new inventory from
     * inside an InventoryClickEvent handler is unsafe on some server versions;
     * scheduling it one tick later avoids the desync.
     */
    private void later(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }
}
