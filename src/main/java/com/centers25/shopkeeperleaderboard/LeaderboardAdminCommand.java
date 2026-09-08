package com.centers25.shopkeeperleaderboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

final class LeaderboardAdminCommand implements CommandExecutor, TabCompleter {
    private final StatsRepository repository;

    LeaderboardAdminCommand(StatsRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!sender.hasPermission("shopkeeperleaderboard.admin")) {
            sender.sendRichMessage("<red>You do not have permission to manage the leaderboard.</red>");
            return true;
        }
        if (arguments.length < 2) {
            usage(sender, label);
            return true;
        }
        String action = arguments[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "clearall" -> clearAll(sender, label, arguments);
            case "clear" -> clear(sender, label, arguments);
            case "reduce" -> reduce(sender, label, arguments);
            default -> {
                usage(sender, label);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        if (!sender.hasPermission("shopkeeperleaderboard.admin")) return List.of();
        if (arguments.length == 1) return matching(arguments[0], List.of("clear", "reduce", "clearall"));
        if (arguments.length == 2 && arguments[0].equalsIgnoreCase("clearall")) return matching(arguments[1], List.of("confirm"));
        if (arguments.length == 2) return matching(arguments[1], repository.playerNames());
        if (arguments.length == 3 && arguments[0].equalsIgnoreCase("clear")) return matching(arguments[2], List.of("trades", "diamonds", "all"));
        if (arguments.length == 3 && arguments[0].equalsIgnoreCase("reduce")) return matching(arguments[2], List.of("trades", "diamonds"));
        return List.of();
    }

    private boolean clearAll(CommandSender sender, String label, String[] arguments) {
        if (arguments.length != 2 || !arguments[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage(Component.text("Run /" + label + " clearall confirm to continue.", NamedTextColor.RED));
            return true;
        }
        int cleared = repository.clearAll();
        repository.save();
        sender.sendMessage(Component.text("Cleared " + cleared + " seller record(s).", NamedTextColor.GREEN));
        return true;
    }

    private boolean clear(CommandSender sender, String label, String[] arguments) {
        if (arguments.length > 3) {
            usage(sender, label);
            return true;
        }
        StatType type = arguments.length == 2 ? StatType.ALL : StatType.parse(arguments[2]).orElse(null);
        if (type == null) {
            usage(sender, label);
            return true;
        }
        if (!repository.clear(arguments[1], type)) {
            sender.sendRichMessage("<red>No recorded seller was found for <white>" + arguments[1] + "</white>.</red>");
            return true;
        }
        repository.save();
        sender.sendRichMessage("<green>Cleared <white>" + type.name().toLowerCase(Locale.ROOT)
                + "</white> for <white>" + arguments[1] + "</white>.</green>");
        return true;
    }

    private boolean reduce(CommandSender sender, String label, String[] arguments) {
        if (arguments.length != 4) {
            usage(sender, label);
            return true;
        }
        StatType type = StatType.parse(arguments[2]).orElse(null);
        if (type == null || type == StatType.ALL) {
            usage(sender, label);
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(arguments[3]);
        } catch (NumberFormatException error) {
            sender.sendRichMessage("<red>The amount must be a positive whole number.</red>");
            return true;
        }
        if (amount <= 0) {
            sender.sendRichMessage("<red>The amount must be greater than zero.</red>");
            return true;
        }
        if (!repository.reduce(arguments[1], type, amount)) {
            sender.sendRichMessage("<red>No recorded seller was found for <white>" + arguments[1] + "</white>.</red>");
            return true;
        }
        repository.save();
        sender.sendRichMessage("<green>Reduced <white>" + arguments[1] + "</white>'s <white>"
                + type.name().toLowerCase(Locale.ROOT) + "</white> by <white>" + amount + "</white>.</green>");
        return true;
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("/" + label + " clear <player> [trades|diamonds|all]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " reduce <player> <trades|diamonds> <amount>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " clearall confirm", NamedTextColor.GRAY));
    }

    private List<String> matching(String prefix, List<String> values) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
