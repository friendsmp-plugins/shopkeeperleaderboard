package com.centers25.shopkeeperleaderboard;

import com.nisovin.shopkeepers.api.events.ShopkeeperTradeCompletedEvent;
import com.nisovin.shopkeepers.api.events.ShopkeeperTradeEvent;
import com.nisovin.shopkeepers.api.shopkeeper.player.PlayerShopkeeper;
import com.nisovin.shopkeepers.api.util.UnmodifiableItemStack;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;

final class TradeListener implements Listener {
    private final StatsRepository repository;
    private final Map<Material, Long> currencyValues;

    TradeListener(StatsRepository repository, Map<Material, Long> currencyValues) {
        this.repository = repository;
        this.currencyValues = Map.copyOf(currencyValues);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTradeCompleted(ShopkeeperTradeCompletedEvent event) {
        if (!(event.getShopkeeper() instanceof PlayerShopkeeper shopkeeper)) return;
        ShopkeeperTradeEvent trade = event.getCompletedTrade();
        long first = itemValue(trade.getReceivedItem1());
        long second = itemValue(trade.getReceivedItem2());
        long value = first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
        if (value > 0) repository.recordSale(shopkeeper.getOwnerUUID(), shopkeeper.getOwnerName(), value);
    }

    private long itemValue(UnmodifiableItemStack item) {
        if (item == null) return 0;
        long unit = currencyValues.getOrDefault(item.getType(), 0L);
        if (unit <= 0 || item.getAmount() <= 0) return 0;
        try {
            return Math.multiplyExact(unit, item.getAmount());
        } catch (ArithmeticException error) {
            return Long.MAX_VALUE;
        }
    }
}
