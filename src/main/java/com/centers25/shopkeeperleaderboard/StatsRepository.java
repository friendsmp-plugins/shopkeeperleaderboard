package com.centers25.shopkeeperleaderboard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class StatsRepository {
    private final ShopkeeperLeaderboardPlugin plugin;
    private final Path file;
    private final Map<UUID, SellerStats> sellers = new HashMap<>();
    private boolean dirty;

    StatsRepository(ShopkeeperLeaderboardPlugin plugin) {
        this.plugin = plugin;
        file = plugin.getDataFolder().toPath().resolve("stats.yml");
    }

    void load() {
        sellers.clear();
        if (!Files.isRegularFile(file)) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection section = yaml.getConfigurationSection("sellers");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                String path = "sellers." + key;
                sellers.put(playerId, new SellerStats(playerId,
                        yaml.getString(path + ".name", playerId.toString().substring(0, 8)),
                        yaml.getLong(path + ".trades"), yaml.getLong(path + ".diamond-value")));
            } catch (IllegalArgumentException error) {
                plugin.log().warn("Ignoring invalid seller UUID in stats.yml: " + key, error);
            }
        }
        dirty = false;
    }

    void recordSale(UUID ownerId, String ownerName, long diamondValue) {
        sellers.computeIfAbsent(ownerId, id -> new SellerStats(id, ownerName, 0, 0)).recordSale(ownerName, diamondValue);
        dirty = true;
    }

    Optional<SellerStats> find(String player) {
        try {
            SellerStats byId = sellers.get(UUID.fromString(player));
            if (byId != null) return Optional.of(byId);
        } catch (IllegalArgumentException ignored) {
        }
        String normalized = player.toLowerCase(Locale.ROOT);
        return sellers.values().stream().filter(stats -> stats.playerName().toLowerCase(Locale.ROOT).equals(normalized)).findFirst();
    }

    List<SellerStats> sorted(SortMode mode) {
        Comparator<SellerStats> comparator = mode == SortMode.TRADES
                ? Comparator.comparingLong(SellerStats::trades).reversed().thenComparing(Comparator.comparingLong(SellerStats::diamondValue).reversed())
                : Comparator.comparingLong(SellerStats::diamondValue).reversed().thenComparing(Comparator.comparingLong(SellerStats::trades).reversed());
        return sellers.values().stream().filter(stats -> stats.trades() > 0 || stats.diamondValue() > 0)
                .sorted(comparator.thenComparing(SellerStats::playerName, String.CASE_INSENSITIVE_ORDER))
                .map(SellerStats::copy).toList();
    }

    boolean clear(String player, StatType type) {
        Optional<SellerStats> result = find(player);
        if (result.isEmpty()) return false;
        result.get().clear(type);
        dirty = true;
        return true;
    }

    boolean reduce(String player, StatType type, long amount) {
        Optional<SellerStats> result = find(player);
        if (result.isEmpty()) return false;
        result.get().reduce(type, amount);
        dirty = true;
        return true;
    }

    int clearAll() {
        int cleared = sellers.size();
        sellers.clear();
        dirty = true;
        return cleared;
    }

    List<String> playerNames() {
        return sellers.values().stream().map(SellerStats::playerName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    void saveIfDirty() {
        if (dirty) save();
    }

    void save() {
        try {
            Files.createDirectories(file.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            for (SellerStats stats : sellers.values()) {
                String path = "sellers." + stats.playerId();
                yaml.set(path + ".name", stats.playerName());
                yaml.set(path + ".trades", stats.trades());
                yaml.set(path + ".diamond-value", stats.diamondValue());
            }
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            yaml.save(temporary.toFile());
            move(temporary, file);
            dirty = false;
        } catch (IOException error) {
            plugin.log().error("Could not save seller statistics: " + error.getMessage(), error);
        }
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
