package dev.cadu.chunkloader.command;

import dev.cadu.chunkloader.ChunkLoaderPlugin;
import dev.cadu.chunkloader.Loader;
import dev.cadu.chunkloader.gui.LoaderGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ChunkLoaderCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("give", "list", "info", "name", "remove", "gui", "reload", "help");

    private final ChunkLoaderPlugin plugin;

    public ChunkLoaderCommand(ChunkLoaderPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                openGui(player, false);
            } else {
                help(sender, label);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> give(sender, args);
            case "list" -> list(sender, args);
            case "info" -> info(sender);
            case "name" -> name(sender, label, args);
            case "remove", "delete" -> remove(sender);
            case "gui", "menu" -> gui(sender, args);
            case "reload" -> reload(sender);
            default -> help(sender, label);
        }
        return true;
    }

    // ---- subcommands ----------------------------------------------------------------

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chunkloader.admin")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player '" + args[1] + "' is not online.", NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Component.text("Usage: /chunkloader give <player> [amount]", NamedTextColor.YELLOW));
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text("'" + args[2] + "' is not a number.", NamedTextColor.RED));
                return;
            }
        }

        ItemStack item = plugin.item().create(amount);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        overflow.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));

        plugin.messages().send(sender, "given", "amount", String.valueOf(amount), "player", target.getName());
        if (!target.equals(sender)) {
            plugin.messages().send(target, "received", "amount", String.valueOf(amount));
        }
    }

    private void list(CommandSender sender, String[] args) {
        boolean all = args.length >= 2 && args[1].equalsIgnoreCase("all");
        if (all && !sender.hasPermission("chunkloader.admin")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }

        List<Loader> loaders = loadersFor(sender, all);
        if (loaders.isEmpty()) {
            plugin.messages().send(sender, "list-empty");
            return;
        }
        sender.sendMessage(plugin.messages().render("list-header", "count", String.valueOf(loaders.size())));
        int index = 1;
        for (Loader loader : loaders) {
            sender.sendMessage(plugin.messages().renderBare("list-entry",
                    "index", String.valueOf(index++),
                    "name", loader.displayName(),
                    "world", loader.world(),
                    "x", String.valueOf(loader.x()),
                    "y", String.valueOf(loader.y()),
                    "z", String.valueOf(loader.z()),
                    "chunks", String.valueOf(loader.chunkCount()),
                    "owner", loader.ownerName()));
        }
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use that.", NamedTextColor.RED));
            return;
        }
        Loader loader = targetLoader(player);
        if (loader == null) {
            plugin.messages().send(player, "not-a-loader");
            return;
        }
        player.sendMessage(Component.text(loader.displayName(), NamedTextColor.AQUA));
        player.sendMessage(Component.text("  World: " + loader.world(), NamedTextColor.GRAY));
        player.sendMessage(Component.text(
                "  Location: " + loader.x() + ", " + loader.y() + ", " + loader.z()
                        + " (chunk " + loader.chunkX() + ", " + loader.chunkZ() + ")", NamedTextColor.GRAY));
        player.sendMessage(Component.text(
                "  Radius: " + loader.radius() + " (" + loader.chunkCount() + " chunks)", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Owner: " + loader.ownerName(), NamedTextColor.GRAY));
    }

    private void name(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use that.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "name-usage");
            return;
        }
        Loader loader = targetLoader(player);
        if (loader == null) {
            plugin.messages().send(player, "not-a-loader");
            return;
        }
        if (!loader.owner().equals(player.getUniqueId()) && !player.hasPermission("chunkloader.admin")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        String nickname = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        Loader renamed = plugin.manager().rename(loader, nickname);
        plugin.messages().send(player, "renamed", "name", renamed.displayName());
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use that.", NamedTextColor.RED));
            return;
        }
        Loader loader = targetLoader(player);
        if (loader == null) {
            plugin.messages().send(player, "not-a-loader");
            return;
        }
        if (!loader.owner().equals(player.getUniqueId()) && !player.hasPermission("chunkloader.admin")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        plugin.manager().removeAt(loader.location());
        if (loader.location() != null) {
            loader.location().getBlock().setType(org.bukkit.Material.AIR);
            loader.location().getWorld().dropItemNaturally(
                    loader.location(), plugin.item().createNamed(1, loader.name()));
        }
        plugin.messages().send(player, "removed",
                "name", loader.displayName(),
                "used", String.valueOf(plugin.manager().countOf(player.getUniqueId())),
                "limit", plugin.limitLabel(player));
    }

    private void gui(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the menu.", NamedTextColor.RED));
            return;
        }
        boolean all = args.length >= 2 && args[1].equalsIgnoreCase("all");
        if (all && !sender.hasPermission("chunkloader.admin")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        openGui(player, all);
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("chunkloader.admin")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        plugin.reloadConfig();
        plugin.restartTasks();
        plugin.manager().reapplyAll();
        plugin.messages().send(sender, "reloaded", "loaders", String.valueOf(plugin.manager().all().size()));
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(Component.text("ChunkLoader commands:", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  /" + label + " gui [all]   - open the loader menu", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /" + label + " list [all]  - list loaders", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /" + label + " info        - inspect the loader you look at", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /" + label + " name <text> - rename that loader", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /" + label + " remove      - remove that loader", NamedTextColor.GRAY));
        if (sender.hasPermission("chunkloader.admin")) {
            sender.sendMessage(Component.text("  /" + label + " give <player> [amount] - give loader items", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  /" + label + " reload      - reload the config", NamedTextColor.GRAY));
        }
    }

    // ---- helpers --------------------------------------------------------------------

    private void openGui(Player player, boolean all) {
        player.openInventory(new LoaderGui(player, loadersFor(player, all), all).getInventory());
    }

    private List<Loader> loadersFor(CommandSender sender, boolean all) {
        List<Loader> loaders = new ArrayList<>(
                all || !(sender instanceof Player player)
                        ? plugin.manager().all()
                        : plugin.manager().loadersOf(player.getUniqueId()));
        loaders.sort(Comparator.comparing(Loader::world).thenComparing(Loader::displayName));
        return loaders;
    }

    /** The loader the player is looking at (within 8 blocks) or standing on, else null. */
    private Loader targetLoader(Player player) {
        Block target = player.getTargetBlockExact(8);
        if (target != null) {
            Loader loader = plugin.manager().getAt(target.getLocation());
            if (loader != null) {
                return loader;
            }
        }
        // fall back to the block the player is standing on
        Block below = player.getLocation().getBlock().getRelative(0, -1, 0);
        return plugin.manager().getAt(below.getLocation());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .filter(s -> switch (s) {
                        case "give", "reload" -> sender.hasPermission("chunkloader.admin");
                        default -> true;
                    })
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("chunkloader.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("gui"))
                && sender.hasPermission("chunkloader.admin")) {
            return "all".startsWith(args[1].toLowerCase()) ? List.of("all") : List.of();
        }
        return List.of();
    }
}
