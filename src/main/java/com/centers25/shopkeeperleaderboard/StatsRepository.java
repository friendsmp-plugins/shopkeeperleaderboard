package com.centers25.shopkeeperleaderboard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.function.Consumer;
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
    private final Path file;
    private final Clock clock;
    private final Consumer<String> warning;
    private final Map<TimePeriod, Map<UUID, SellerStats>> records = new EnumMap<>(TimePeriod.class);
    private final Map<TimePeriod, String> starts = new EnumMap<>(TimePeriod.class);
    private boolean dirty;

    StatsRepository(ShopkeeperLeaderboardPlugin plugin) {
        this(plugin.getDataFolder().toPath().resolve("stats.yml"),
                Clock.system(ZoneId.of(plugin.getConfig().getString("period-timezone", "UTC"))),
                message -> plugin.log().warn(message));
    }

    StatsRepository(Path file, Clock clock, Consumer<String> warning) {
        this.file = file;
        this.clock = clock;
        this.warning = warning;
        for (TimePeriod period : TimePeriod.values()) records.put(period, new HashMap<>());
        rollover();
    }

    String periodDescription(TimePeriod period) {
        rollover();
        return period == TimePeriod.ALL_TIME ? "Lifetime totals" : "From " + starts.get(period) + " · " + clock.getZone();
    }

    String periodToken(TimePeriod period) {
        rollover();
        return starts.get(period);
    }

    private void rollover() {
        LocalDate today = LocalDate.now(clock);
        for (TimePeriod period : TimePeriod.values()) {
            String start = period.start(today);
            if (!start.equals(starts.get(period))) {
                records.get(period).clear();
                starts.put(period, start);
                dirty = true;
            }
        }
    }

    void load() {
        rollover();
        records.values().forEach(Map::clear);
        if (!Files.isRegularFile(file)) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        LocalDate today = LocalDate.now(clock);
        for (TimePeriod period : TimePeriod.values()) {
            String prefix = period == TimePeriod.ALL_TIME ? "" : "periods." + period.key() + ".";
            if (period != TimePeriod.ALL_TIME && !period.start(today).equals(yaml.getString(prefix + "start"))) continue;
            ConfigurationSection section = yaml.getConfigurationSection(prefix + "sellers");
            if (section == null) continue;
            for (String key : section.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String path = prefix + "sellers." + key;
                    records.get(period).put(id, new SellerStats(id,
                            yaml.getString(path + ".name", id.toString().substring(0, 8)),
                            yaml.getLong(path + ".trades"), yaml.getLong(path + ".diamond-value")));
                } catch (IllegalArgumentException error) {
                    warning.accept("Ignoring invalid seller UUID in stats.yml: " + key);
                }
            }
        }
        rollover();
        dirty = true;
    }

    void recordSale(UUID ownerId, String ownerName, long diamondValue) {
        rollover();
        String name = ownerName == null || ownerName.isBlank() ? ownerId.toString().substring(0, 8) : ownerName;
        for (Map<UUID, SellerStats> sellers : records.values()) {
            sellers.computeIfAbsent(ownerId, id -> new SellerStats(id, name, 0, 0)).recordSale(name, diamondValue);
        }
        dirty = true;
    }

    Optional<SellerStats> find(String player, TimePeriod period) {
        rollover();
        Map<UUID, SellerStats> sellers = records.get(period);
        try {
            SellerStats byId = sellers.get(UUID.fromString(player));
            if (byId != null) return Optional.of(byId);
        } catch (IllegalArgumentException ignored) {
        }
        String normalized = player.toLowerCase(Locale.ROOT);
        return sellers.values().stream().filter(stats -> stats.playerName().toLowerCase(Locale.ROOT).equals(normalized)).findFirst();
    }

    List<SellerStats> sorted(SortMode mode, TimePeriod period) {
        rollover();
        Map<UUID, SellerStats> sellers = records.get(period);
        Comparator<SellerStats> comparator = mode == SortMode.TRADES
                ? Comparator.comparingLong(SellerStats::trades).reversed().thenComparing(Comparator.comparingLong(SellerStats::diamondValue).reversed())
                : Comparator.comparingLong(SellerStats::diamondValue).reversed().thenComparing(Comparator.comparingLong(SellerStats::trades).reversed());
        return sellers.values().stream().filter(stats -> stats.trades() > 0 || stats.diamondValue() > 0)
                .sorted(comparator.thenComparing(SellerStats::playerName, String.CASE_INSENSITIVE_ORDER).thenComparing(SellerStats::playerId))
                .map(SellerStats::copy).toList();
    }

    boolean clear(String player, StatType type, TimePeriod period) {
        Optional<SellerStats> result = find(player, period);
        if (result.isEmpty()) return false;
        result.get().clear(type);
        dirty = true;
        return true;
    }

    boolean reduce(String player, StatType type, long amount, TimePeriod period) {
        Optional<SellerStats> result = find(player, period);
        if (result.isEmpty()) return false;
        result.get().reduce(type, amount);
        dirty = true;
        return true;
    }

    int clearAll(TimePeriod period) {
        rollover();
        Map<UUID, SellerStats> sellers = records.get(period);
        int cleared = sellers.size();
        sellers.clear();
        dirty = true;
        return cleared;
    }

    List<String> playerNames() {
        rollover();
        return records.values().stream().flatMap(map -> map.values().stream()).map(SellerStats::playerName).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    void saveIfDirty() {
        rollover();
        if (dirty) save();
    }

    boolean save() {
        rollover();
        try {
            Files.createDirectories(file.getParent());
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("schema-version", 2);
            for (TimePeriod period : TimePeriod.values()) {
                String prefix = period == TimePeriod.ALL_TIME ? "" : "periods." + period.key() + ".";
                if (period != TimePeriod.ALL_TIME) yaml.set(prefix + "start", starts.get(period));
                for (SellerStats stats : records.get(period).values()) {
                    String path = prefix + "sellers." + stats.playerId();
                    yaml.set(path + ".name", stats.playerName());
                    yaml.set(path + ".trades", stats.trades());
                    yaml.set(path + ".diamond-value", stats.diamondValue());
                }
            }
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            yaml.save(temporary.toFile());
            move(temporary, file);
            dirty = false;
            return true;
        } catch (IOException error) {
            warning.accept("Could not save seller statistics: " + error.getMessage());
            return false;
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
