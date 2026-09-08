package com.centers25.shopkeeperleaderboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

final class LeaderboardAdminCommand implements CommandExecutor, TabCompleter {
    private final ShopkeeperLeaderboardPlugin plugin;
    private final StatsRepository repository;
    private final LeaderboardMenu menu;

    LeaderboardAdminCommand(ShopkeeperLeaderboardPlugin plugin, StatsRepository repository, LeaderboardMenu menu) {
        this.plugin = plugin;
        this.repository = repository;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shopkeeperleaderboard.admin")) {
            message(sender, "You do not have permission to manage the leaderboard.", true);
            return true;
        }
        if (args.length == 0 && sender instanceof Player player) {
            menu.openAdmin(player);
            return true;
        }
        if (args.length < 2) { usage(sender, label); return true; }
        String action = args[0].toLowerCase(Locale.ROOT);
        int base = switch (action) { case "clearall" -> 2; case "clear" -> 3; case "reduce" -> 4; default -> -1; };
        if (base < 0 || args.length > base + 1 || args.length < base && !(action.equals("clear") && args.length == 2)) {
            usage(sender, label);
            return true;
        }
        TimePeriod period = args.length == base + 1 ? TimePeriod.parse(args[base]).orElse(null) : TimePeriod.ALL_TIME;
        if (period == null) { usage(sender, label); return true; }
        if (action.equals("clearall")) {
            if (!args[1].equalsIgnoreCase("confirm")) { usage(sender, label); return true; }
            int count = repository.clearAll(period);
            finish(sender, "Cleared " + count + " seller record(s) · " + period.label);
            return true;
        }
        StatType type = args.length == 2 ? StatType.ALL : StatType.parse(args[2]).orElse(null);
        if (type == null || action.equals("reduce") && type == StatType.ALL) { usage(sender, label); return true; }
        boolean changed;
        String summary;
        if (action.equals("reduce")) {
            long amount;
            try { amount = Long.parseLong(args[3]); }
            catch (NumberFormatException error) { message(sender, "Amount must be a positive whole number.", true); return true; }
            if (amount <= 0) { message(sender, "Amount must be greater than zero.", true); return true; }
            changed = repository.reduce(args[1], type, amount, period);
            summary = "Reduced " + args[1] + " · " + type.name().toLowerCase(Locale.ROOT) + " by " + amount;
        } else {
            changed = repository.clear(args[1], type, period);
            summary = "Cleared " + args[1] + " · " + type.name().toLowerCase(Locale.ROOT);
        }
        if (!changed) message(sender, "No seller found in " + period.label.toLowerCase(Locale.ROOT) + ": " + args[1], true);
        else finish(sender, summary + " · " + period.label);
        return true;
    }

    private void finish(CommandSender sender, String summary) {
        boolean saved = repository.save();
        plugin.getLogger().info(sender.getName() + ": " + summary);
        message(sender, summary + (saved ? "." : ". Save failed; check the server log."), !saved);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("shopkeeperleaderboard.admin")) return List.of();
        List<String> options = List.of();
        if (args.length == 1) options = List.of("clear", "reduce", "clearall");
        else if (args.length == 2) options = args[0].equalsIgnoreCase("clearall") ? List.of("confirm") : repository.playerNames();
        else if (args.length == 3 && args[0].equalsIgnoreCase("clear")) options = List.of("trades", "diamonds", "all");
        else if (args.length == 3 && args[0].equalsIgnoreCase("reduce")) options = List.of("trades", "diamonds");
        else if (args.length == 4 && args[0].equalsIgnoreCase("reduce")) options = List.of("1", "10", "100", "1000");
        else if (args.length == 3 && args[0].equalsIgnoreCase("clearall")
                || args.length == 4 && args[0].equalsIgnoreCase("clear")
                || args.length == 5 && args[0].equalsIgnoreCase("reduce")) options = List.of("alltime", "weekly", "monthly");
        if (args.length == 0) return options;
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private void usage(CommandSender sender, String label) {
        message(sender, "/" + label + " — open administration", false);
        message(sender, "/" + label + " clear <player> [trades|diamonds|all] [period]", false);
        message(sender, "/" + label + " reduce <player> <trades|diamonds> <amount> [period]", false);
        message(sender, "/" + label + " clearall confirm [period]", false);
        message(sender, "Periods: alltime, weekly, monthly. Default: alltime. Edits affect only the selected period.", false);
    }

    private void message(CommandSender sender, String value, boolean error) {
        sender.sendMessage(Component.text(value, error ? NamedTextColor.RED : NamedTextColor.GRAY));
    }
}
