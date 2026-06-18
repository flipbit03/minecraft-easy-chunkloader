package dev.cadu.chunkloader.listener;

import dev.cadu.chunkloader.ChunkLoaderPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Re-applies chunk tickets when a world loads after the plugin enabled (e.g. worlds
 * created/loaded at runtime by Multiverse), since loaders in not-yet-loaded worlds are
 * skipped during the initial {@code reapplyAll()}.
 */
public final class WorldListener implements Listener {

    private final ChunkLoaderPlugin plugin;

    public WorldListener(ChunkLoaderPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.manager().reapplyAll();
    }
}
