package dev.cadu.chunkloader;

import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

/**
 * Thin MiniMessage helper: resolves a message from config by key, prepends the configured
 * prefix and substitutes {@code <name>} placeholders. Keeps every user-facing string in
 * config.yml so server owners can fully re-skin / translate the plugin. Rendered to legacy
 * text by {@link LegacyMiniMessage}, so it works on plain Spigot.
 */
public final class Messages {

    private final ChunkLoaderPlugin plugin;

    public Messages(ChunkLoaderPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Text with every {@code §} removed, so player/world names and other values can never
     * carry legacy formatting codes into a message, item or menu.
     */
    public static String literal(String text) {
        return text.replace("\u00a7", "");
    }

    // getString(path) without an explicit default falls back to the jar's config.yml, so a
    // config.yml saved by an older release still gets messages added since.
    private String raw(String key) {
        String message = plugin.getConfig().getString("messages." + key);
        return message != null ? message : "<red>missing message: " + key + "</red>";
    }

    /** Renders {@code messages.<key>} with the prefix and the given {@code key, value, ...} pairs. */
    public String render(String key, String... placeholders) {
        String prefix = plugin.getConfig().getString("prefix");
        if (prefix == null) {
            prefix = "";
        }
        return LegacyMiniMessage.render(prefix + raw(key), resolvers(placeholders));
    }

    /** Renders a message without the prefix (used for multi-line list entries). */
    public String renderBare(String key, String... placeholders) {
        return LegacyMiniMessage.render(raw(key), resolvers(placeholders));
    }

    public void send(CommandSender to, String key, String... placeholders) {
        to.sendMessage(render(key, placeholders));
    }

    private Map<String, String> resolvers(String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders must be key/value pairs");
        }
        Map<String, String> resolvers = new HashMap<>();
        for (int i = 0; i < placeholders.length; i += 2) {
            resolvers.putIfAbsent(placeholders[i], literal(placeholders[i + 1]));
        }
        return resolvers;
    }
}
