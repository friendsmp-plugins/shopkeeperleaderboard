package com.centers25.shopkeeperleaderboard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class LeaderboardCommand implements CommandExecutor, TabCompleter {
    private final LeaderboardMenu menu;
    LeaderboardCommand(LeaderboardMenu menu) { this.menu = menu; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Only players can open the leaderboard.</red>");
            return true;
        }
        SortMode mode = SortMode.DIAMONDS;
        TimePeriod period = TimePeriod.ALL_TIME;
        boolean hasMode = false, hasPeriod = false;
        for (String argument : arguments) {
            if (!hasMode && SortMode.parse(argument).isPresent()) {
                mode = SortMode.parse(argument).orElseThrow();
                hasMode = true;
            } else if (!hasPeriod && TimePeriod.parse(argument).isPresent()) {
                period = TimePeriod.parse(argument).orElseThrow();
                hasPeriod = true;
            } else {
                sender.sendRichMessage("<gray>Usage: /" + label + " [diamonds|trades] [alltime|weekly|monthly]</gray>");
                return true;
            }
        }
        menu.open(player, mode, period, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        if (arguments.length < 1 || arguments.length > 2) return List.of();
        List<String> options = new ArrayList<>();
        if (arguments.length == 1 || SortMode.parse(arguments[0]).isEmpty()) options.addAll(List.of("diamonds", "trades"));
        if (arguments.length == 1 || TimePeriod.parse(arguments[0]).isEmpty()) options.addAll(List.of("alltime", "weekly", "monthly"));
        String prefix = arguments[arguments.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
