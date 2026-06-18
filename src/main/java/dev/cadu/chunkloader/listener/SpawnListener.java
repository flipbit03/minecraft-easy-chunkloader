package dev.cadu.chunkloader.listener;

import dev.cadu.chunkloader.ChunkLoaderPlugin;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

import java.util.EnumSet;
import java.util.Set;

/**
 * Stops mobs from spawning on top of a loader block. A loader (a Lodestone by default) is a
 * full solid block, so hostiles can spawn on its top face whenever a player is in range to
 * drive spawns. Only world-driven spawns are blocked - deliberate player actions (spawn
 * eggs, breeding, /summon, built golems, …) are left alone so admins can still place things
 * on a loader on purpose.
 */
public final class SpawnListener implements Listener {

    /** Spawn reasons that represent a deliberate player/plugin action; never blocked. */
    private static final Set<SpawnReason> DELIBERATE = EnumSet.of(
            SpawnReason.SPAWNER_EGG,
            SpawnReason.DISPENSE_EGG,
            SpawnReason.EGG,
            SpawnReason.BREEDING,
            SpawnReason.BUILD_IRONGOLEM,
            SpawnReason.BUILD_SNOWMAN,
            SpawnReason.BUILD_WITHER,
            SpawnReason.BUCKET,
            SpawnReason.CURED,
            SpawnReason.COMMAND,
            SpawnReason.CUSTOM);

    private final ChunkLoaderPlugin plugin;

    public SpawnListener(ChunkLoaderPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("prevent-mob-spawns-on-loader", true)) {
            return;
        }
        if (DELIBERATE.contains(event.getSpawnReason())) {
            return;
        }
        // The block the mob would stand on is directly below its spawn location.
        Block below = event.getLocation().getBlock().getRelative(BlockFace.DOWN);
        if (plugin.manager().isLoader(below.getLocation())) {
            event.setCancelled(true);
        }
    }
}
