package com.centers25.shopkeeperleaderboard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

final class LeaderboardCommand implements CommandExecutor, TabCompleter {
    private final LeaderboardMenu menu;

    LeaderboardCommand(LeaderboardMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Only players can open the leaderboard.</red>");
            return true;
        }
        SortMode mode = arguments.length == 0 ? SortMode.DIAMONDS : SortMode.parse(arguments[0]).orElse(null);
        if (mode == null || arguments.length > 1) {
            sender.sendRichMessage("<red>Usage: /" + label + " [diamonds|trades]</red>");
            return true;
        }
        menu.open(player, mode, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        return arguments.length == 1 ? List.of("diamonds", "trades") : List.of();
    }
}
