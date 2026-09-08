package com.centers25.shopkeeperleaderboard;

import java.util.Locale;
import java.util.Optional;

enum SortMode {
    DIAMONDS, TRADES;

    static Optional<SortMode> parse(String value) {
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }
}
