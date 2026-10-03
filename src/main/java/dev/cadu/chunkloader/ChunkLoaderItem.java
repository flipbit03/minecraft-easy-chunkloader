package dev.cadu.chunkloader;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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
        if (material == null || !material.isBlock() || !material.isItem()) {
            plugin.getLogger().warning("loader-material '" + plugin.getConfig().getString("loader-material")
                    + "' is not a placeable block; falling back to LODESTONE.");
            return Material.LODESTONE;
        }
        return material;
    }

    /** A fresh stack of chunk loaders. */
    public ItemStack create(int amount) {
        return build(amount, DEFAULT_NAME);
    }

    /** A chunk loader stack pre-named with {@code name} (as if renamed in an anvil). */
    public ItemStack createNamed(int amount, String name) {
        return build(amount, name != null && !name.isBlank() ? name : DEFAULT_NAME);
    }

    /**
     * Builds the item from the exact name/lore components the earlier (Paper) releases wrote:
     * coloured text with italic switched off. {@code setDisplayName}/{@code setLore} would look
     * the same but store differently shaped components, so loaders handed out before and after
     * the move to Spigot would no longer stack with each other.
     */
    @SuppressWarnings("deprecation") // Material#getKeyOrThrow() is Spigot-only; Paper lacks it
    private ItemStack build(int amount, String name) {
        String components = "[custom_name=" + text(name, "aqua")
                + ",lore=[" + text("Place to keep the surrounding", "gray")
                + "," + text("chunks loaded and ticking.", "gray")
                + ",\"\""
                + "," + text("Rename in an anvil to set its", "dark_gray")
                + "," + text("nickname (e.g. \"Iron Farm\").", "dark_gray")
                + "," + text("Only an admin can remove it once placed.", "dark_gray")
                + "],enchantment_glint_override=true]";
        ItemStack item = Bukkit.getItemFactory().createItemStack(material().getKey() + components);
        item.setAmount(Math.max(1, amount));

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** An SNBT text component: {@code text} in {@code color}, not italic. */
    private static String text(String text, String color) {
        String quoted = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{text:\"" + quoted + "\",color:\"" + color + "\",italic:false}";
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
        String plain = ChatColor.stripColor(meta.getDisplayName()).trim();
        return plain.isEmpty() || plain.equals(DEFAULT_NAME) ? null : plain;
    }
}
