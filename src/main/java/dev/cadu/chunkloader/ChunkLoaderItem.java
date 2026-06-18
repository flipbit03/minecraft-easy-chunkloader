package dev.cadu.chunkloader;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Builds the Chunk Loader item and recognises it again later. The item is just the
 * configured {@code loader-material} carrying a persistent-data marker, so a vanilla
 * block of the same material placed by hand is never mistaken for a chunk loader.
 */
public final class ChunkLoaderItem {

    /** Default item name; anything else means the player renamed it (and wants that nickname). */
    public static final String DEFAULT_NAME = "Chunk Loader";

    private final ChunkLoaderPlugin plugin;
    private final NamespacedKey markerKey;

    public ChunkLoaderItem(ChunkLoaderPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "chunk_loader");
    }

    public NamespacedKey markerKey() {
        return markerKey;
    }

    public Material material() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("loader-material", "LODESTONE"));
        if (material == null || !material.isBlock()) {
            plugin.getLogger().warning("loader-material '" + plugin.getConfig().getString("loader-material")
                    + "' is not a placeable block; falling back to LODESTONE.");
            return Material.LODESTONE;
        }
        return material;
    }

    /** A fresh stack of chunk loaders. */
    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(DEFAULT_NAME, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Place to keep the surrounding", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("chunks loaded and ticking.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Rename in an anvil to set its", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("nickname (e.g. \"Iron Farm\").", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Only an admin can remove it once placed.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        meta.setEnchantmentGlintOverride(true);

        item.setItemMeta(meta);
        return item;
    }

    /** A chunk loader stack pre-named with {@code name} (as if renamed in an anvil). */
    public ItemStack createNamed(int amount, String name) {
        ItemStack item = create(amount);
        if (name != null && !name.isBlank()) {
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(name, NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** True if the stack is one of our chunk loaders (checks the marker, not the name). */
    public boolean isChunkLoader(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    /**
     * The nickname the player gave this item by renaming it in an anvil, or {@code null}
     * if it still has the default name. This is what becomes the placed loader's nickname.
     */
    public String customName(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(meta.displayName()).trim();
        return plain.isEmpty() || plain.equals(DEFAULT_NAME) ? null : plain;
    }
}
