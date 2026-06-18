package dev.cadu.chunkloader.listener;

import dev.cadu.chunkloader.ChunkLoaderPlugin;
import dev.cadu.chunkloader.Loader;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Turns placed Chunk Loader items into registered loaders and tears them down on break.
 * Also makes loader blocks resilient to explosions, fire and pistons so an active loader
 * isn't lost (and its tickets orphaned) by something other than a deliberate break.
 */
public final class LoaderBlockListener implements Listener {

    private final ChunkLoaderPlugin plugin;

    public LoaderBlockListener(ChunkLoaderPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        if (!plugin.item().isChunkLoader(inHand)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("chunkloader.use")) {
            event.setCancelled(true);
            plugin.messages().send(player, "no-permission");
            return;
        }
        Location location = event.getBlock().getLocation();
        Loader loader = plugin.manager().create(location, player, plugin.item().customName(inHand));
        plugin.messages().send(player, "placed",
                "name", loader.displayName(),
                "chunks", String.valueOf(loader.chunkCount()),
                "world", loader.world(),
                "x", String.valueOf(loader.x()),
                "y", String.valueOf(loader.y()),
                "z", String.valueOf(loader.z()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Loader loader = plugin.manager().getAt(event.getBlock().getLocation());
        if (loader == null) {
            return;
        }
        Player player = event.getPlayer();
        // Anti-grief: a loader block can only be mined by an admin/op. Everyone else is
        // blocked outright so a normal player can't break, relocate or steal a loader.
        if (!player.hasPermission("chunkloader.admin")) {
            event.setCancelled(true);
            plugin.messages().send(player, "protected");
            return;
        }

        plugin.manager().removeAt(event.getBlock().getLocation());
        // Hand the loader back (keeping its nickname) instead of the vanilla block drop.
        event.setDropItems(false);
        event.getBlock().getWorld().dropItemNaturally(
                event.getBlock().getLocation(), plugin.item().createNamed(1, loader.name()));
        plugin.messages().send(player, "removed", "name", loader.displayName());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.manager().isLoader(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.manager().isLoader(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (plugin.manager().isLoader(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesLoader(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesLoader(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean movesLoader(Iterable<Block> blocks) {
        for (Block block : blocks) {
            if (plugin.manager().isLoader(block.getLocation())) {
                return true;
            }
        }
        return false;
    }
}
