package com.centers25.shopkeeperleaderboard;

import com.centers25.core.backup.PluginBackups;
import com.centers25.core.logging.PluginLogger;
import com.centers25.core.logging.PluginLogs;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class ShopkeeperLeaderboardPlugin extends JavaPlugin {
    private StatsRepository repository;
    private PluginLogger log;

    @Override
    public void onEnable() {
        log = PluginLogs.get(this);
        saveDefaultConfig();
        try {
            repository = new StatsRepository(this);
        } catch (java.time.DateTimeException error) {
            log.error("Invalid period-timezone in config.yml: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        repository.load();
        PluginBackups.get(this).register(this);
        Map<Material, Long> currencyValues = loadCurrencyValues();
        if (currencyValues.isEmpty()) {
            log.error("No valid currency values are configured.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        LeaderboardMenu menu = new LeaderboardMenu(this, repository);
        LeaderboardCommand leaderboardCommand = new LeaderboardCommand(menu);
        PluginCommand leaderboard = Objects.requireNonNull(getCommand("leaderboard"));
        leaderboard.setExecutor(leaderboardCommand);
        leaderboard.setTabCompleter(leaderboardCommand);
        LeaderboardAdminCommand adminCommand = new LeaderboardAdminCommand(this, repository, menu);
        PluginCommand admin = Objects.requireNonNull(getCommand("leaderboardadmin"));
        admin.setExecutor(adminCommand);
        admin.setTabCompleter(adminCommand);
        getServer().getPluginManager().registerEvents(new TradeListener(repository, currencyValues), this);
        getServer().getPluginManager().registerEvents(menu, this);
        long autosaveSeconds = Math.max(10, getConfig().getLong("autosave-seconds", 300));
        getServer().getScheduler().runTaskTimer(this, repository::saveIfDirty,
                autosaveSeconds * 20L, autosaveSeconds * 20L);
        log.info("Tracking Shopkeepers seller trades using " + currencyValues.size() + " currency item(s).");
    }

    @Override
    public void onDisable() {
        if (repository != null) repository.saveIfDirty();
        PluginBackups.get(this).unregister(this);
    }

    PluginLogger log() {
        return log;
    }

    private Map<Material, Long> loadCurrencyValues() {
        Map<Material, Long> values = new EnumMap<>(Material.class);
        ConfigurationSection section = getConfig().getConfigurationSection("currency-values");
        if (section == null) return values;
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            long value = section.getLong(key);
            if (material == null || !material.isItem() || value <= 0) {
                log.warn("Ignoring invalid currency value: " + key + ": " + value);
            } else {
                values.put(material, value);
            }
        }
        return values;
    }
}
