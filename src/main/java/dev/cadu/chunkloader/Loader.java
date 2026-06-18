package dev.cadu.chunkloader;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * A single placed chunk loader. Identified by its block location; the location key is
 * also the entry key in loaders.yml. The {@code radius} is frozen at creation time so
 * that changing {@code chunk-radius} in the config never silently resizes existing
 * loaders (which would add/remove tickets players never asked for).
 */
public record Loader(
        String name,
        String world,
        int x,
        int y,
        int z,
        int radius,
        UUID owner,
        String ownerName,
        long created
) {

    /** Stable identity used as the map / data-file key, e.g. {@code world;10;64;-30}. */
    public String key() {
        return world + ";" + x + ";" + y + ";" + z;
    }

    /** Human-friendly label: the nickname if set, otherwise the coordinates. */
    public String displayName() {
        return name == null || name.isBlank() ? "Loader @ " + x + ", " + y + ", " + z : name;
    }

    /** Returns a copy of this loader with a new nickname. */
    public Loader withName(String newName) {
        return new Loader(newName, world, x, y, z, radius, owner, ownerName, created);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    /** Number of chunks this loader keeps loaded: a (2r+1)x(2r+1) square. */
    public int chunkCount() {
        int side = radius * 2 + 1;
        return side * side;
    }

    /** The world this loader lives in, or {@code null} if it is not currently loaded. */
    public World bukkitWorld() {
        return Bukkit.getWorld(world);
    }

    /** Block location, or {@code null} if the world is not loaded. */
    public Location location() {
        World w = bukkitWorld();
        return w == null ? null : new Location(w, x, y, z);
    }
}
