package dev.cadu.chunkloader;

import dev.cadu.chunkloader.command.ChunkLoaderCommand;
import dev.cadu.chunkloader.listener.GuiListener;
import dev.cadu.chunkloader.listener.LoaderBlockListener;
import dev.cadu.chunkloader.listener.SpawnListener;
import dev.cadu.chunkloader.listener.WorldListener;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ChunkLoaderPlugin extends JavaPlugin {

    private ChunkLoaderItem item;
    private ChunkLoaderManager manager;
    private Messages messages;

    private BukkitTask particleTask;
    private BukkitTask revalidateTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.item = new ChunkLoaderItem(this);
        this.manager = new ChunkLoaderManager(this);
        this.messages = new Messages(this);

        manager.load();
        manager.reapplyAll();

        getServer().getPluginManager().registerEvents(new LoaderBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnListener(this), this);

        PluginCommand command = getCommand("chunkloader");
        if (command != null) {
            ChunkLoaderCommand executor = new ChunkLoaderCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        restartTasks();

        getLogger().info("minecraft-easy-chunkloader enabled - " + manager.all().size()
                + " loader(s) keeping chunks alive.");
    }

    @Override
    public void onDisable() {
        cancelTasks();
        if (manager != null) {
            // Free the chunks so a reload/disable doesn't leave dangling tickets; loaders.yml
            // still holds them and reapplyAll() restores everything on the next enable.
            manager.releaseAllTickets();
        }
    }

    // ---- accessors ------------------------------------------------------------------

    public ChunkLoaderItem item() {
        return item;
    }

    public ChunkLoaderManager manager() {
        return manager;
    }

    public Messages messages() {
        return messages;
    }

    // ---- scheduled tasks ------------------------------------------------------------

    /** (Re)schedules the ambient-particle and revalidation tasks from current config. */
    public void restartTasks() {
        cancelTasks();

        if (getConfig().getBoolean("particles.enabled", true)) {
            long interval = Math.max(1, getConfig().getLong("particles.interval-ticks", 40));
            particleTask = getServer().getScheduler().runTaskTimer(this, this::showParticles, interval, interval);
        }

        long revalidate = getConfig().getLong("revalidate-interval-ticks", 1200);
        if (revalidate > 0) {
            revalidateTask = getServer().getScheduler()
                    .runTaskTimer(this, manager::revalidate, revalidate, revalidate);
        }
    }

    private void cancelTasks() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        if (revalidateTask != null) {
            revalidateTask.cancel();
            revalidateTask = null;
        }
    }

    private void showParticles() {
        Particle particle;
        try {
            particle = Particle.valueOf(getConfig().getString("particles.type", "ELECTRIC_SPARK").toUpperCase());
        } catch (IllegalArgumentException ex) {
            return; // bad particle name in config; skip silently until corrected
        }
        if (particle.getDataType() != Void.class) {
            return; // particle needs extra data we don't supply; ignore to stay safe
        }
        int count = Math.max(1, getConfig().getInt("particles.count", 8));
        for (Loader loader : manager.all()) {
            World world = loader.bukkitWorld();
            if (world == null || !world.isChunkLoaded(loader.chunkX(), loader.chunkZ())) {
                continue;
            }
            if (world.getBlockAt(loader.x(), loader.y(), loader.z()).getType() != item.material()) {
                continue;
            }
            Location at = new Location(world, loader.x() + 0.5, loader.y() + 1.1, loader.z() + 0.5);
            world.spawnParticle(particle, at, count, 0.25, 0.3, 0.25, 0.0);
        }
    }
}
