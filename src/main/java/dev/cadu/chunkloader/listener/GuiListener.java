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
 * Drives the {@link LoaderGui}. The menu is view-only; the only action is an admin
 * shift-click to remove a loader. There is deliberately no click-to-teleport (that would
 * be a free teleport to any saved location).
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

        // Only action: admin shift-click to remove. Any other click does nothing.
        if (!event.isShiftClick()) {
            return;
        }
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
        plugin.messages().send(player, "removed", "name", loader.displayName());
        player.closeInventory();
    }
}
