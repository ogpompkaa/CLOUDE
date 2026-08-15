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
 * Paginated GUI front-end for homes.
 *
 * <p>{@code /homes} opens a chest menu of the player's homes rendered as their
 * own player head, with world/coordinate lore. Left-click teleports (via the
 * warm-up flow), right-click opens a Confirm/Cancel menu; only Confirm performs
 * the async delete, after which the list is re-queried and reopened on the same
 * (clamped) page. A bottom navigation row carries the page indicator and
 * previous/next arrows.
 */
public final class HomeGui {

    /** Content slots per page: the top five rows; the sixth is navigation. */
    private static final int PAGE_SIZE = 45;
    private static final int MENU_SIZE = 54;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

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

    public void open(Player player) {
        open(player, 0);
    }

    /** Fetches the player's homes off-thread, then opens {@code page} on the main thread. */
    public void open(Player player, int page) {
        repository.findAll(player.getUniqueId()).thenAccept(list ->
                plugin.database().sync(() -> showHomes(player, list, page))
        ).exceptionally(throwable -> {
            plugin.getSLF4JLogger().warn("Failed to open homes GUI for {}", player.getName(), throwable);
            plugin.database().sync(() -> feedback.error(player, "command.error"));
            return null;
        });
    }

    private void showHomes(Player player, List<Home> list, int requestedPage) {
        if (list.isEmpty()) {
            player.closeInventory();
            feedback.error(player, "home.list-empty");
            return;
        }

        int totalPages = (list.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        Menu menu = new Menu(messages.render("gui.homes.title",
                MessageService.placeholder("page", String.valueOf(page + 1)),
                MessageService.placeholder("pages", String.valueOf(totalPages))), MENU_SIZE);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, list.size());
        for (int i = start; i < end; i++) {
            Home home = list.get(i);
            menu.setButton(i - start, homeIcon(player, home), event -> {
                if (event.isRightClick()) {
                    later(() -> showConfirm(player, home.name(), page));
                } else {
                    teleportTo(player, home);
                }
            });
        }

        buildNavigation(menu, player, list, page, totalPages);
        menu.open(player);
    }

    private ItemStack homeIcon(Player player, Home home) {
        Component name = messages.render("gui.homes.entry-name",
                MessageService.placeholder("name", home.name()));
        List<Component> lore = List.of(
                messages.render("gui.homes.entry-world",
                        MessageService.placeholder("world", home.world())),
                messages.render("gui.homes.entry-coords",
                        MessageService.placeholder("x", String.valueOf((int) Math.floor(home.x()))),
                        MessageService.placeholder("y", String.valueOf((int) Math.floor(home.y()))),
                        MessageService.placeholder("z", String.valueOf((int) Math.floor(home.z())))),
                Component.empty(),
                messages.render("gui.homes.entry-tp"),
                messages.render("gui.homes.entry-del"));
        return Icons.head(player, name, lore);
    }

    private void buildNavigation(Menu menu, Player player, List<Home> list, int page, int totalPages) {
        ItemStack filler = Icons.of(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = PAGE_SIZE; slot < MENU_SIZE; slot++) {
            menu.setButton(slot, filler, null);
        }

        menu.setButton(SLOT_INFO, Icons.of(Material.PAPER,
                messages.render("gui.nav.page",
                        MessageService.placeholder("page", String.valueOf(page + 1)),
                        MessageService.placeholder("pages", String.valueOf(totalPages))),
                List.of()), null);

        if (page > 0) {
            menu.setButton(SLOT_PREV, Icons.of(Material.ARROW,
                            messages.render("gui.nav.prev"), List.of()),
                    event -> later(() -> showHomes(player, list, page - 1)));
        }
        if (page < totalPages - 1) {
            menu.setButton(SLOT_NEXT, Icons.of(Material.ARROW,
                            messages.render("gui.nav.next"), List.of()),
                    event -> later(() -> showHomes(player, list, page + 1)));
        }
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

    private void showConfirm(Player player, String name, int page) {
        Menu menu = new Menu(
                messages.render("gui.confirm.title", MessageService.placeholder("name", name)), 27);

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
                event -> confirmDelete(player, name, page));

        menu.setButton(15, Icons.of(Material.RED_WOOL,
                        messages.render("gui.confirm.no"),
                        List.of(messages.render("gui.confirm.no-lore"))),
                event -> later(() -> open(player, page)));

        menu.open(player);
    }

    private void confirmDelete(Player player, String name, int page) {
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
                    open(player, page); // refresh; showHomes clamps the page if it shrank
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
