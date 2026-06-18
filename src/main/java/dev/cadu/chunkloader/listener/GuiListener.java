package dev.cadu.chunkloader.listener;

import dev.cadu.chunkloader.ChunkLoaderPlugin;
import dev.cadu.chunkloader.Loader;
import dev.cadu.chunkloader.gui.LoaderGui;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Drives the {@link LoaderGui}: left-click teleports to a loader, shift-click removes it.
 * Removal is allowed for the loader's owner or anyone with {@code chunkloader.admin}.
 */
public final class GuiListener implements Listener {

    private final ChunkLoaderPlugin plugin;

    public GuiListener(ChunkLoaderPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LoaderGui gui)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != gui.getInventory() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Loader loader = gui.loaderAt(event.getSlot());
        if (loader == null) {
            return;
        }

        if (event.isShiftClick()) {
            if (!player.hasPermission("chunkloader.admin")) {
                plugin.messages().send(player, "protected");
                return;
            }
            Location location = loader.location();
            plugin.manager().removeAt(loader.location());
            // return the item to the world / give it back, mirroring a manual break
            if (location != null && location.getWorld() != null) {
                location.getBlock().setType(org.bukkit.Material.AIR);
                location.getWorld().dropItemNaturally(location, plugin.item().createNamed(1, loader.name()));
            }
            int used = plugin.manager().countOf(loader.owner());
            plugin.messages().send(player, "removed",
                    "name", loader.displayName(),
                    "used", String.valueOf(used),
                    "limit", plugin.limitLabel(player));
            player.closeInventory();
            return;
        }

        Location location = loader.location();
        if (location == null) {
            plugin.messages().send(player, "not-a-loader");
            return;
        }
        player.closeInventory();
        // Chunk is held loaded by our ticket, so the async teleport resolves immediately.
        player.teleportAsync(location.toCenterLocation().add(0, 1, 0));
    }
}
