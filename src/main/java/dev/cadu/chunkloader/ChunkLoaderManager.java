package dev.cadu.chunkloader;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the set of placed chunk loaders: persistence (loaders.yml) and the Paper plugin
 * chunk tickets that actually keep chunks loaded.
 *
 * <p>Tickets are held via {@link World#addPluginChunkTicket(int, int, org.bukkit.plugin.Plugin)}.
 * A plugin ticket keeps a chunk fully loaded and ticking until it is explicitly removed,
 * and Paper persists our tickets across restarts; we still re-apply them on enable so the
 * authoritative source of truth is always loaders.yml, not whatever Paper happened to save.
 */
public final class ChunkLoaderManager {

    private final ChunkLoaderPlugin plugin;
    private final File dataFile;
    private final Map<String, Loader> loaders = new ConcurrentHashMap<>();

    public ChunkLoaderManager(ChunkLoaderPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "loaders.yml");
    }

    // ---- configuration helpers ------------------------------------------------------

    /** The radius new loaders are created with, clamped to {@code max-radius}. */
    public int configuredRadius() {
        int radius = plugin.getConfig().getInt("chunk-radius", 0);
        int max = Math.max(0, plugin.getConfig().getInt("max-radius", 4));
        return Math.max(0, Math.min(radius, max));
    }

    // ---- registry queries -----------------------------------------------------------

    public Loader getAt(Location location) {
        return loaders.get(keyOf(location));
    }

    public boolean isLoader(Location location) {
        return loaders.containsKey(keyOf(location));
    }

    public Collection<Loader> all() {
        return List.copyOf(loaders.values());
    }

    public List<Loader> loadersOf(UUID owner) {
        List<Loader> result = new ArrayList<>();
        for (Loader loader : loaders.values()) {
            if (loader.owner().equals(owner)) {
                result.add(loader);
            }
        }
        return result;
    }

    // ---- mutations ------------------------------------------------------------------

    /** Registers a loader at {@code location} owned by {@code owner}, applies its tickets and saves. */
    public Loader create(Location location, Player owner, String name) {
        Loader loader = new Loader(
                name,
                location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                configuredRadius(),
                owner.getUniqueId(), owner.getName(),
                System.currentTimeMillis());
        loaders.put(loader.key(), loader);
        applyTicket(loader);
        save();
        return loader;
    }

    /** Renames the given loader (does not move it or touch its tickets). */
    public Loader rename(Loader loader, String name) {
        Loader renamed = loader.withName(name);
        loaders.put(renamed.key(), renamed);
        save();
        return renamed;
    }

    /** Removes the loader at {@code location} (if any), drops its tickets and saves. */
    public Loader removeAt(Location location) {
        Loader loader = loaders.remove(keyOf(location));
        if (loader != null) {
            removeTicket(loader);
            save();
        }
        return loader;
    }

    // ---- chunk tickets --------------------------------------------------------------

    private void applyTicket(Loader loader) {
        World world = loader.bukkitWorld();
        if (world == null) {
            return; // world not loaded yet; reapplyAll() runs again on WorldLoadEvent
        }
        int radius = loader.radius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                world.addPluginChunkTicket(loader.chunkX() + dx, loader.chunkZ() + dz, plugin);
            }
        }
        debug("Added tickets for loader " + loader.key() + " (" + loader.chunkCount() + " chunks)");
    }

    private void removeTicket(Loader loader) {
        World world = loader.bukkitWorld();
        if (world == null) {
            return;
        }
        int radius = loader.radius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = loader.chunkX() + dx;
                int cz = loader.chunkZ() + dz;
                // Only drop a ticket if no *other* loader still wants this chunk, so
                // overlapping loaders don't unload each other's shared chunks.
                if (!chunkWantedByOther(loader, world.getName(), cx, cz)) {
                    world.removePluginChunkTicket(cx, cz, plugin);
                }
            }
        }
        debug("Removed tickets for loader " + loader.key());
    }

    private boolean chunkWantedByOther(Loader self, String world, int cx, int cz) {
        for (Loader other : loaders.values()) {
            if (other.key().equals(self.key()) || !other.world().equals(world)) {
                continue;
            }
            int r = other.radius();
            if (Math.abs(cx - other.chunkX()) <= r && Math.abs(cz - other.chunkZ()) <= r) {
                return true;
            }
        }
        return false;
    }

    /** Re-applies tickets for every known loader. Safe to call repeatedly (tickets are idempotent). */
    public void reapplyAll() {
        for (Loader loader : loaders.values()) {
            applyTicket(loader);
        }
    }

    /**
     * Drops loaders whose block no longer matches the loader material (e.g. removed by
     * WorldEdit or an admin command that fired no break event) and re-applies tickets for
     * the rest. Only inspects worlds/chunks that are loaded.
     */
    public void revalidate() {
        org.bukkit.Material expected = plugin.item().material();
        List<Loader> stale = new ArrayList<>();
        for (Loader loader : loaders.values()) {
            World world = loader.bukkitWorld();
            if (world == null) {
                continue;
            }
            if (world.isChunkLoaded(loader.chunkX(), loader.chunkZ())
                    && world.getBlockAt(loader.x(), loader.y(), loader.z()).getType() != expected) {
                stale.add(loader);
            } else {
                applyTicket(loader);
            }
        }
        for (Loader loader : stale) {
            plugin.getLogger().info("Removing orphaned chunk loader at " + loader.key()
                    + " (block no longer present).");
            loaders.remove(loader.key());
            removeTicket(loader);
        }
        if (!stale.isEmpty()) {
            save();
        }
    }

    /** Removes every ticket we hold (called on disable). Does not touch loaders.yml. */
    public void releaseAllTickets() {
        for (Loader loader : loaders.values()) {
            World world = loader.bukkitWorld();
            if (world != null) {
                int radius = loader.radius();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        world.removePluginChunkTicket(loader.chunkX() + dx, loader.chunkZ() + dz, plugin);
                    }
                }
            }
        }
    }

    // ---- persistence ----------------------------------------------------------------

    public void load() {
        loaders.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (Map<?, ?> raw : yaml.getMapList("loaders")) {
            try {
                Loader loader = new Loader(
                        raw.get("name") == null ? null : String.valueOf(raw.get("name")),
                        String.valueOf(raw.get("world")),
                        ((Number) raw.get("x")).intValue(),
                        ((Number) raw.get("y")).intValue(),
                        ((Number) raw.get("z")).intValue(),
                        ((Number) raw.get("radius")).intValue(),
                        UUID.fromString(String.valueOf(raw.get("owner"))),
                        raw.get("owner-name") == null ? "unknown" : String.valueOf(raw.get("owner-name")),
                        raw.get("created") == null ? 0L : ((Number) raw.get("created")).longValue());
                loaders.put(loader.key(), loader);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipping malformed loader entry in loaders.yml: " + raw);
            }
        }
        plugin.getLogger().info("Loaded " + loaders.size() + " chunk loader(s).");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Loader loader : loaders.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (loader.name() != null) {
                map.put("name", loader.name());
            }
            map.put("world", loader.world());
            map.put("x", loader.x());
            map.put("y", loader.y());
            map.put("z", loader.z());
            map.put("radius", loader.radius());
            map.put("owner", loader.owner().toString());
            map.put("owner-name", loader.ownerName());
            map.put("created", loader.created());
            list.add(map);
        }
        yaml.set("loaders", list);
        try {
            yaml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save loaders.yml: " + ex.getMessage());
        }
    }

    // ---- internals ------------------------------------------------------------------

    private static String keyOf(Location location) {
        return location.getWorld().getName() + ";"
                + location.getBlockX() + ";"
                + location.getBlockY() + ";"
                + location.getBlockZ();
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info(message);
        }
    }
}
