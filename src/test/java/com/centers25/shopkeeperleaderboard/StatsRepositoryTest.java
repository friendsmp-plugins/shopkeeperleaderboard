package com.centers25.shopkeeperleaderboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StatsRepositoryTest {
    @TempDir Path directory;
    private final UUID seller = UUID.randomUUID();
    private final MutableClock clock = new MutableClock("2026-09-08T12:00:00Z", "UTC");

    private StatsRepository repository() {
        return new StatsRepository(directory.resolve("stats.yml"), clock, message -> fail(message));
    }

    @Test void migratesLegacyTotalsWithoutInventingHistory() throws Exception {
        Files.writeString(directory.resolve("stats.yml"), "sellers:\n  " + seller + ":\n    name: Seller\n    trades: 12\n    diamond-value: 45\n");
        StatsRepository stats = repository();
        stats.load();
        assertEquals(45, stats.find("seller", TimePeriod.ALL_TIME).orElseThrow().diamondValue());
        assertTrue(stats.sorted(SortMode.TRADES, TimePeriod.WEEKLY).isEmpty());
        assertTrue(stats.sorted(SortMode.TRADES, TimePeriod.MONTHLY).isEmpty());
        stats.recordSale(seller, "Seller", 9);
        assertTrue(stats.save());
        StatsRepository restored = repository();
        restored.load();
        assertEquals(54, restored.find("Seller", TimePeriod.ALL_TIME).orElseThrow().diamondValue());
        assertEquals(9, restored.find("Seller", TimePeriod.WEEKLY).orElseThrow().diamondValue());
        assertEquals(1, restored.find("Seller", TimePeriod.MONTHLY).orElseThrow().trades());
    }

    @Test void mondayRolloverPreservesMonthAndLifetime() {
        StatsRepository stats = repository();
        stats.recordSale(seller, "Seller", 9);
        clock.instant = Instant.parse("2026-09-14T00:00:00Z");
        assertTrue(stats.sorted(SortMode.TRADES, TimePeriod.WEEKLY).isEmpty());
        assertEquals(1, stats.sorted(SortMode.TRADES, TimePeriod.MONTHLY).size());
        stats.recordSale(seller, "Seller", 3);
        assertEquals(2, stats.find("Seller", TimePeriod.ALL_TIME).orElseThrow().trades());
        assertEquals(1, stats.find("Seller", TimePeriod.WEEKLY).orElseThrow().trades());
    }

    @Test void monthRolloverPreservesCurrentWeek() {
        clock.instant = Instant.parse("2026-09-30T23:59:59Z");
        StatsRepository stats = repository();
        stats.recordSale(seller, "Seller", 7);
        clock.instant = Instant.parse("2026-10-01T00:00:00Z");
        assertTrue(stats.sorted(SortMode.DIAMONDS, TimePeriod.MONTHLY).isEmpty());
        assertEquals(7, stats.find("Seller", TimePeriod.WEEKLY).orElseThrow().diamondValue());
        assertEquals(7, stats.find("Seller", TimePeriod.ALL_TIME).orElseThrow().diamondValue());
    }

    @Test void restartDiscardsExpiredPeriods() {
        StatsRepository stats = repository();
        stats.recordSale(seller, "Seller", 1);
        stats.save();
        clock.instant = Instant.parse("2027-01-01T00:00:00Z");
        StatsRepository restored = repository();
        restored.load();
        assertTrue(restored.sorted(SortMode.DIAMONDS, TimePeriod.WEEKLY).isEmpty());
        assertTrue(restored.sorted(SortMode.DIAMONDS, TimePeriod.MONTHLY).isEmpty());
        assertEquals(1, restored.sorted(SortMode.DIAMONDS, TimePeriod.ALL_TIME).size());
    }

    @Test void respectsConfiguredTimezone() {
        MutableClock local = new MutableClock("2026-09-13T18:59:59Z", "Asia/Karachi");
        StatsRepository stats = new StatsRepository(directory.resolve("stats.yml"), local, message -> fail(message));
        stats.recordSale(seller, "Seller", 1);
        assertEquals("2026-09-07", stats.periodToken(TimePeriod.WEEKLY));
        local.instant = Instant.parse("2026-09-13T19:00:00Z");
        assertEquals("2026-09-14", stats.periodToken(TimePeriod.WEEKLY));
        assertTrue(stats.sorted(SortMode.TRADES, TimePeriod.WEEKLY).isEmpty());
    }

    @Test void correctionsAreScopedClampedAndPersisted() {
        StatsRepository stats = repository();
        stats.recordSale(seller, "Seller", 9);
        assertTrue(stats.reduce(seller.toString(), StatType.DIAMONDS, Long.MAX_VALUE, TimePeriod.WEEKLY));
        assertEquals(0, stats.find("Seller", TimePeriod.WEEKLY).orElseThrow().diamondValue());
        assertEquals(9, stats.find("Seller", TimePeriod.MONTHLY).orElseThrow().diamondValue());
        assertTrue(stats.clear("Seller", StatType.TRADES, TimePeriod.MONTHLY));
        assertEquals(9, stats.find("Seller", TimePeriod.MONTHLY).orElseThrow().diamondValue());
        assertEquals(1, stats.clearAll(TimePeriod.ALL_TIME));
        assertTrue(stats.sorted(SortMode.TRADES, TimePeriod.ALL_TIME).isEmpty());
        stats.save();
        StatsRepository restored = repository();
        restored.load();
        assertEquals(0, restored.find("Seller", TimePeriod.MONTHLY).orElseThrow().trades());
        assertEquals(1, restored.find("Seller", TimePeriod.WEEKLY).orElseThrow().trades());
        assertFalse(restored.clear("Missing", StatType.ALL, TimePeriod.WEEKLY));
    }

    @Test void sortsBothMetricsAndReturnsIndependentSnapshots() {
        StatsRepository stats = repository();
        UUID other = UUID.randomUUID();
        stats.recordSale(seller, "Alpha", 100);
        stats.recordSale(other, "Beta", 1);
        stats.recordSale(other, "Beta", 1);
        assertEquals("Alpha", stats.sorted(SortMode.DIAMONDS, TimePeriod.WEEKLY).getFirst().playerName());
        assertEquals("Beta", stats.sorted(SortMode.TRADES, TimePeriod.MONTHLY).getFirst().playerName());
        stats.sorted(SortMode.DIAMONDS, TimePeriod.WEEKLY).getFirst().clear(StatType.ALL);
        assertEquals(100, stats.find("Alpha", TimePeriod.WEEKLY).orElseThrow().diamondValue());
    }

    @Test void saturatesOverflowAndHandlesMissingNames() {
        StatsRepository stats = repository();
        stats.recordSale(seller, null, Long.MAX_VALUE);
        stats.recordSale(seller, "UpdatedName", 1);
        for (TimePeriod period : TimePeriod.values()) {
            SellerStats value = stats.find("updatedname", period).orElseThrow();
            assertEquals(Long.MAX_VALUE, value.diamondValue());
            assertEquals(2, value.trades());
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;
        MutableClock(String instant, String zone) { this.instant = Instant.parse(instant); this.zone = ZoneId.of(zone); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
