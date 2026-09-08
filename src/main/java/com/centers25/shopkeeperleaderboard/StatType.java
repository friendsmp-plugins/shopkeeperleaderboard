package com.centers25.shopkeeperleaderboard;

import java.util.Locale;
import java.util.Optional;

enum StatType {
    TRADES, DIAMONDS, ALL;

    static Optional<StatType> parse(String value) {
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }
}
