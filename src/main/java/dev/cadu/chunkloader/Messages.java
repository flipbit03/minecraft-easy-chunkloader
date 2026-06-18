package dev.cadu.chunkloader;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/**
 * Thin MiniMessage helper: resolves a message from config by key, prepends the configured
 * prefix and substitutes {@code <name>} placeholders. Keeps every user-facing string in
 * config.yml so server owners can fully re-skin / translate the plugin.
 */
public final class Messages {

    private final ChunkLoaderPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public Messages(ChunkLoaderPlugin plugin) {
        this.plugin = plugin;
    }

    private String raw(String key) {
        return plugin.getConfig().getString("messages." + key, "<red>missing message: " + key + "</red>");
    }

    /** Renders {@code messages.<key>} with the prefix and the given {@code key, value, ...} pairs. */
    public Component render(String key, String... placeholders) {
        String prefix = plugin.getConfig().getString("prefix", "");
        return mm.deserialize(prefix + raw(key), resolvers(placeholders));
    }

    /** Renders a message without the prefix (used for multi-line list entries). */
    public Component renderBare(String key, String... placeholders) {
        return mm.deserialize(raw(key), resolvers(placeholders));
    }

    public void send(CommandSender to, String key, String... placeholders) {
        to.sendMessage(render(key, placeholders));
    }

    private TagResolver[] resolvers(String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders must be key/value pairs");
        }
        TagResolver[] resolvers = new TagResolver[placeholders.length / 2];
        for (int i = 0; i < placeholders.length; i += 2) {
            resolvers[i / 2] = Placeholder.unparsed(placeholders[i], placeholders[i + 1]);
        }
        return resolvers;
    }
}
