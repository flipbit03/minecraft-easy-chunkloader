package dev.cadu.chunkloader.gui;

import dev.cadu.chunkloader.Loader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * A chest-style menu listing chunk loaders, one compass per loader showing its nickname
 * and location. The holder keeps the slot -> loader mapping so the click listener can map
 * a clicked slot straight back to a loader. View-only; admins may shift-click to remove.
 */
public final class LoaderGui implements InventoryHolder {

    private final Inventory inventory;
    private final List<Loader> slots = new ArrayList<>();
    private final boolean adminView;
    private final boolean canManage;

    public LoaderGui(Player viewer, List<Loader> loaders, boolean adminView) {
        this.adminView = adminView;
        this.canManage = viewer.hasPermission("chunkloader.admin");
        int size = Math.min(54, Math.max(9, ((loaders.size() + 8) / 9) * 9));
        this.inventory = Bukkit.createInventory(this, size,
                Component.text("Chunk Loaders", NamedTextColor.DARK_AQUA));

        for (Loader loader : loaders) {
            if (slots.size() >= size) {
                break; // capped at one chest; /cl list shows the full set
            }
            inventory.addItem(icon(loader));
            slots.add(loader);
        }
    }

    private ItemStack icon(Loader loader) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(loader.displayName(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(line(loader.world() + " @ " + loader.x() + ", " + loader.y() + ", " + loader.z(),
                NamedTextColor.GRAY));
        lore.add(line(loader.chunkCount() + " chunk(s) loaded", NamedTextColor.GRAY));
        if (adminView) {
            lore.add(line("Owner: " + loader.ownerName(), NamedTextColor.DARK_GRAY));
        }
        if (canManage) {
            lore.add(Component.empty());
            lore.add(line("Shift-click: remove", NamedTextColor.RED));
        }
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /** The loader shown in {@code slot}, or {@code null} if that slot is empty. */
    public Loader loaderAt(int slot) {
        return slot >= 0 && slot < slots.size() ? slots.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
