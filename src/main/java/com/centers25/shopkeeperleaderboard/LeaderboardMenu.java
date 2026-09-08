package com.centers25.shopkeeperleaderboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class LeaderboardMenu implements Listener {
    private static final int[] PLAYER_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private final ShopkeeperLeaderboardPlugin plugin;
    private final StatsRepository repository;

    LeaderboardMenu(ShopkeeperLeaderboardPlugin plugin, StatsRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    void open(Player player, SortMode mode, TimePeriod period, int page) {
        browse(player, mode, period, page, false);
    }

    void openAdmin(Player player) {
        browse(player, SortMode.DIAMONDS, TimePeriod.ALL_TIME, 0, true);
    }

    private void browse(Player player, SortMode mode, TimePeriod period, int requestedPage, boolean admin) {
        if (admin && !allowed(player)) return;
        List<SellerStats> sellers = repository.sorted(mode, period);
        int pages = Math.max(1, (sellers.size() + PLAYER_SLOTS.length - 1) / PLAYER_SLOTS.length);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        Holder view = new Holder(admin);
        Inventory inventory = create(view, 54, admin ? "Leaderboard · Administration" : "Shopkeeper Leaderboard");
        for (int slot = 9; slot < 18; slot++) inventory.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " "));
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " "));
        for (TimePeriod option : TimePeriod.values()) {
            int slot = 2 + option.ordinal();
            button(view, slot, selected(option == period, option == TimePeriod.ALL_TIME ? Material.CLOCK : Material.PAPER,
                    option.label, option == period ? "Selected" : "View rankings"),
                    () -> browse(player, mode, option, 0, admin));
        }
        button(view, 6, item(mode == SortMode.DIAMONDS ? Material.DIAMOND : Material.BOOK,
                "Sort: " + (mode == SortMode.DIAMONDS ? "Diamonds" : "Sales"), "Click to change"),
                () -> browse(player, mode == SortMode.DIAMONDS ? SortMode.TRADES : SortMode.DIAMONDS, period, 0, admin));
        inventory.setItem(13, item(Material.PAPER, period.label, repository.periodDescription(period)));
        int offset = page * PLAYER_SLOTS.length;
        for (int index = 0; index < PLAYER_SLOTS.length && offset + index < sellers.size(); index++) {
            SellerStats stats = sellers.get(offset + index);
            int slot = PLAYER_SLOTS[index];
            inventory.setItem(slot, head(stats, offset + index + 1, admin));
            if (admin) view.actions.put(slot, () -> edit(player, stats.playerId(), mode, period, page, StatType.TRADES, 1));
        }
        if (sellers.isEmpty()) inventory.setItem(31, item(Material.PAPER, "No recorded sales", "Completed trades will appear here."));
        if (page > 0) button(view, 45, item(Material.ARROW, "Previous page"), () -> browse(player, mode, period, page - 1, admin));
        if (page + 1 < pages) button(view, 53, item(Material.ARROW, "Next page"), () -> browse(player, mode, period, page + 1, admin));
        inventory.setItem(49, item(Material.PAPER, "Page " + (page + 1) + " / " + pages, sellers.size() + " sellers"));
        button(view, 50, item(Material.SUNFLOWER, "Refresh"), () -> browse(player, mode, period, page, admin));
        if (admin) {
            button(view, 47, item(Material.BOOK, "Player view"), () -> open(player, mode, period, page));
            button(view, 51, item(Material.REDSTONE, "Clear period", period.label + " · all sellers", "Review before applying"),
                    () -> confirm(player, period, "Clear all sellers", period.label + " · sales and diamonds",
                            () -> repository.clearAll(period), () -> browse(player, mode, period, 0, true)));
        } else {
            int rank = -1;
            SellerStats own = null;
            for (int i = 0; i < sellers.size(); i++) {
                if (sellers.get(i).playerId().equals(player.getUniqueId())) { rank = i + 1; own = sellers.get(i); break; }
            }
            inventory.setItem(47, own == null ? item(Material.NAME_TAG, "Your position", "Unranked this period") : head(own, rank, false));
            if (player.hasPermission("shopkeeperleaderboard.admin")) button(view, 51, item(Material.COMPARATOR, "Administration"),
                    () -> browse(player, mode, period, page, true));
        }
        player.openInventory(inventory);
    }

    private void edit(Player player, UUID id, SortMode mode, TimePeriod period, int page, StatType type, long amount) {
        if (!allowed(player)) return;
        SellerStats stats = repository.find(id.toString(), period).orElse(null);
        if (stats == null) { browse(player, mode, period, page, true); return; }
        Holder view = new Holder(true);
        Inventory inventory = create(view, 45, "Administration · Seller");
        inventory.setItem(4, head(stats, 0, false));
        inventory.setItem(13, item(Material.PAPER, period.label, repository.periodDescription(period), "Edits affect this period only."));
        button(view, 19, selected(type == StatType.TRADES, Material.BOOK, "Sales", "Select statistic"),
                () -> edit(player, id, mode, period, page, StatType.TRADES, amount));
        button(view, 20, selected(type == StatType.DIAMONDS, Material.DIAMOND, "Diamonds", "Select statistic"),
                () -> edit(player, id, mode, period, page, StatType.DIAMONDS, amount));
        long[] amounts = {1, 10, 100, 1000};
        for (int i = 0; i < amounts.length; i++) {
            long choice = amounts[i];
            button(view, 23 + i, selected(amount == choice, Material.PAPER, number(choice), "Reduction amount"),
                    () -> edit(player, id, mode, period, page, type, choice));
        }
        Runnable back = () -> edit(player, id, mode, period, page, type, amount);
        String label = type == StatType.TRADES ? "sales" : "diamonds";
        long current = type == StatType.TRADES ? stats.trades() : stats.diamondValue();
        button(view, 30, item(Material.IRON_NUGGET, "Reduce " + label, number(current) + " → " + number(Math.max(0, current - Math.min(current, amount))), "Review reduction of " + number(amount)),
                () -> confirm(player, period, "Reduce " + label + " by " + number(amount), stats.playerName() + " · " + period.label,
                        () -> repository.reduce(id.toString(), type, amount, period), back));
        button(view, 32, item(Material.REDSTONE, "Clear " + label, "Set selected statistic to zero"),
                () -> confirm(player, period, "Clear " + label, stats.playerName() + " · " + period.label,
                        () -> repository.clear(id.toString(), type, period), back));
        button(view, 34, item(Material.REDSTONE_BLOCK, "Clear seller", "Set sales and diamonds to zero"),
                () -> confirm(player, period, "Clear seller", stats.playerName() + " · " + period.label,
                        () -> repository.clear(id.toString(), StatType.ALL, period), back));
        button(view, 36, item(Material.ARROW, "Back to sellers"), () -> browse(player, mode, period, page, true));
        inventory.setItem(40, item(Material.WRITABLE_BOOK, "Custom amount", "/lbadmin reduce " + stats.playerName(),
                label.equals("sales") ? "trades <amount> " + commandPeriod(period) : "diamonds <amount> " + commandPeriod(period)));
        player.openInventory(inventory);
    }

    private void confirm(Player player, TimePeriod period, String title, String detail, Runnable mutation, Runnable back) {
        if (!allowed(player)) return;
        String token = repository.periodToken(period);
        Holder view = new Holder(true);
        Inventory inventory = create(view, 27, "Administration · Confirm");
        inventory.setItem(4, item(Material.PAPER, title, detail, "This change cannot be undone."));
        button(view, 11, item(Material.GRAY_DYE, "Cancel", "Return without changes"), back);
        button(view, 15, item(Material.RED_DYE, "Confirm change", "Apply to " + period.label.toLowerCase(Locale.ROOT)), () -> {
            if (!token.equals(repository.periodToken(period))) {
                player.sendMessage(text("The period changed. Review the current totals before editing.", NamedTextColor.RED));
                back.run();
                return;
            }
            mutation.run();
            boolean saved = repository.save();
            plugin.getLogger().info(player.getName() + " (" + player.getUniqueId() + "): " + title + " — " + detail);
            player.sendMessage(text(saved ? "Leaderboard updated." : "Updated in memory; saving failed. Check the server log.",
                    saved ? NamedTextColor.AQUA : NamedTextColor.RED));
            back.run();
        });
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder view)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (view.admin && !allowed(player)) { player.closeInventory(); return; }
        Runnable action = view.actions.get(event.getRawSlot());
        if (action == null || view.pending) return;
        view.pending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != view.inventory) return;
            if (view.admin && !allowed(player)) { player.closeInventory(); return; }
            action.run();
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) event.setCancelled(true);
    }

    private boolean allowed(Player player) {
        if (player.hasPermission("shopkeeperleaderboard.admin")) return true;
        player.sendMessage(text("You do not have permission to manage the leaderboard.", NamedTextColor.RED));
        return false;
    }

    private Inventory create(Holder view, int size, String title) {
        view.inventory = Bukkit.createInventory(view, size, text(title, NamedTextColor.WHITE));
        return view.inventory;
    }

    private void button(Holder view, int slot, ItemStack item, Runnable action) {
        view.inventory.setItem(slot, item);
        view.actions.put(slot, action);
    }

    private ItemStack head(SellerStats stats, int rank, boolean admin) {
        ItemStack head = item(Material.PLAYER_HEAD, (rank > 0 ? "#" + rank + "  " : "") + stats.playerName(),
                "Sales: " + number(stats.trades()), "Diamonds: " + number(stats.diamondValue()));
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(stats.playerId()));
        if (admin) {
            var lore = new java.util.ArrayList<>(meta.lore());
            lore.add(text("Click to manage", NamedTextColor.AQUA));
            meta.lore(lore);
        }
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack selected(boolean selected, Material material, String name, String hint) {
        ItemStack item = item(material, name, selected ? "Selected" : hint);
        if (selected) {
            ItemMeta meta = item.getItemMeta();
            meta.displayName(text(name, NamedTextColor.AQUA));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name, NamedTextColor.WHITE));
        meta.lore(Arrays.stream(lore).map(line -> text(line, NamedTextColor.GRAY)).toList());
        item.setItemMeta(meta);
        return item;
    }

    static String commandPeriod(TimePeriod period) { return period == TimePeriod.ALL_TIME ? "alltime" : period.key(); }
    private static String number(long value) { return String.format(Locale.US, "%,d", value); }
    private static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private static final class Holder implements InventoryHolder {
        private final boolean admin;
        private final Map<Integer, Runnable> actions = new HashMap<>();
        private Inventory inventory;
        private boolean pending;
        private Holder(boolean admin) { this.admin = admin; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
