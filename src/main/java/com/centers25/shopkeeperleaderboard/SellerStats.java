package com.centers25.shopkeeperleaderboard;

import java.util.UUID;

final class SellerStats {
    private final UUID playerId;
    private String playerName;
    private long trades;
    private long diamondValue;

    SellerStats(UUID playerId, String playerName, long trades, long diamondValue) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.trades = Math.max(0, trades);
        this.diamondValue = Math.max(0, diamondValue);
    }

    UUID playerId() { return playerId; }
    String playerName() { return playerName; }
    long trades() { return trades; }
    long diamondValue() { return diamondValue; }

    void recordSale(String latestName, long value) {
        if (latestName != null && !latestName.isBlank()) playerName = latestName;
        trades = add(trades, 1);
        diamondValue = add(diamondValue, Math.max(0, value));
    }

    void clear(StatType type) {
        if (type == StatType.TRADES || type == StatType.ALL) trades = 0;
        if (type == StatType.DIAMONDS || type == StatType.ALL) diamondValue = 0;
    }

    void reduce(StatType type, long amount) {
        if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
        if (type == StatType.TRADES) trades = Math.max(0, trades - Math.min(trades, amount));
        else if (type == StatType.DIAMONDS) diamondValue = Math.max(0, diamondValue - Math.min(diamondValue, amount));
        else throw new IllegalArgumentException("all cannot be reduced");
    }

    SellerStats copy() { return new SellerStats(playerId, playerName, trades, diamondValue); }

    private static long add(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }
}
