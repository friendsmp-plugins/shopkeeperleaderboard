package com.centers25.shopkeeperleaderboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class LeaderboardMenu implements Listener {
    private static final int[] PLAYER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private final StatsRepository repository;

    LeaderboardMenu(StatsRepository repository) {
        this.repository = repository;
    }

    void open(Player player, SortMode mode, int requestedPage) {
        List<SellerStats> sellers = repository.sorted(mode);
        int totalPages = Math.max(1, (sellers.size() + PLAYER_SLOTS.length - 1) / PLAYER_SLOTS.length);
        int page = Math.clamp(requestedPage, 0, totalPages - 1);
        Holder holder = new Holder(mode, page);
        Inventory inventory = Bukkit.createInventory(holder, 54, plain("Seller Leaderboard", NamedTextColor.WHITE));
        holder.inventory = inventory;
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "));
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        for (int slot : PLAYER_SLOTS) inventory.clear(slot);
        inventory.setItem(3, modeButton(SortMode.TRADES, mode));
        inventory.setItem(5, modeButton(SortMode.DIAMONDS, mode));
        int offset = page * PLAYER_SLOTS.length;
        for (int index = 0; index < PLAYER_SLOTS.length && offset + index < sellers.size(); index++) {
            inventory.setItem(PLAYER_SLOTS[index], playerHead(sellers.get(offset + index), offset + index + 1));
        }
        if (page > 0) inventory.setItem(45, item(Material.ARROW, plain("Previous", NamedTextColor.WHITE)));
        inventory.setItem(49, item(Material.PAPER, plain("Page " + (page + 1) + " of " + totalPages, NamedTextColor.WHITE),
                plain(sellers.size() + " sellers", NamedTextColor.GRAY)));
        if (page + 1 < totalPages) inventory.setItem(53, item(Material.ARROW, plain("Next", NamedTextColor.WHITE)));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        switch (event.getSlot()) {
            case 3 -> open(player, SortMode.TRADES, 0);
            case 5 -> open(player, SortMode.DIAMONDS, 0);
            case 45 -> open(player, holder.mode, holder.page - 1);
            case 53 -> open(player, holder.mode, holder.page + 1);
            default -> { }
        }
    }

    private ItemStack playerHead(SellerStats stats, int rank) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        OfflinePlayer player = Bukkit.getOfflinePlayer(stats.playerId());
        meta.setOwningPlayer(player);
        meta.displayName(plain("#" + rank + "  " + stats.playerName(), NamedTextColor.WHITE));
        meta.lore(List.of(statLine("Sales", stats.trades()), statLine("Diamonds", stats.diamondValue())));
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack modeButton(SortMode button, SortMode active) {
        boolean selected = button == active;
        return item(button == SortMode.DIAMONDS ? Material.DIAMOND : Material.BOOK,
                plain(button == SortMode.DIAMONDS ? "Sort by Diamonds" : "Sort by Sales", NamedTextColor.WHITE),
                plain(selected ? "Selected" : "Click to select", NamedTextColor.GRAY));
    }

    private ItemStack item(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) meta.lore(Arrays.stream(lore).map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private Component statLine(String label, long value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(String.format(Locale.US, "%,d", value), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static final class Holder implements InventoryHolder {
        private final SortMode mode;
        private final int page;
        private Inventory inventory;

        private Holder(SortMode mode, int page) {
            this.mode = mode;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
