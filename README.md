# Shopkeeper Leaderboard

Shopkeepers seller rankings for Paper 26.2, with lifetime, current-week and current-month totals. Requires Shopkeepers and PluginCore.

## Player menu

`/leaderboard` opens lifetime rankings sorted by diamonds. Use the top tabs to select All time, Weekly or Monthly, and the sort button to switch between sales and diamonds. The footer contains personal rank, pagination and refresh. Administrators also see an Administration button.

Examples:

- `/leaderboard weekly`
- `/leaderboard trades monthly`
- `/leaderboard monthly diamonds`

The interface uses white titles, grey supporting text and aqua for selected controls. Each page shows up to 21 sellers. Period totals refresh when reopening the menu or using Refresh.

## Administration

`/leaderboardadmin` (alias `/lbadmin`) opens the administrator menu. Permission: `shopkeeperleaderboard.admin` (operators by default).

Select a period and seller, select Sales or Diamonds, then choose a reduction amount (1, 10, 100 or 1,000). The editor previews the resulting value. Reduction, clearing a statistic, clearing a seller and clearing a period each require confirmation. Values never go below zero. Changes are saved immediately and logged with the administrator's identity. Confirmation expires if its calendar period changes. Inventory clicks and drags cannot move menu items.

Console and custom amounts remain supported:

```text
/lbadmin clear <player-or-uuid> [trades|diamonds|all] [alltime|weekly|monthly]
/lbadmin reduce <player-or-uuid> <trades|diamonds> <amount> [alltime|weekly|monthly]
/lbadmin clearall confirm [alltime|weekly|monthly]
```

Omitting a period targets lifetime totals. To clear a specific period for a seller, include the statistic, e.g. `/lbadmin clear Steve all weekly`. Every edit affects only the selected period; it does not change the other two rankings. `clearall confirm` clears lifetime records, while `clearall confirm weekly` clears the current week. These command operations apply immediately; the GUI offers the review step.

## Periods and migration

Weeks begin Monday at 00:00; months begin on the first day at 00:00. `period-timezone` is an IANA timezone, defaulting to `UTC`; use `Asia/Karachi` if desired. Configure it before beginning period tracking and restart after changes. Changing timezone later may discard a current-period bucket if its start date changes.

Existing `stats.yml` lifetime records are preserved. Old totals have no timestamps and are not copied into weekly or monthly rankings. Both new rankings start accumulating trades after upgrade. Current-period buckets persist across restarts and automatically roll over on access or autosave; expired periods are not archived. Both rankings keep sales and diamond values independently of the chosen sort.

## Build and verification

Use Java 26 and Gradle:

```text
gradle test jar
```

Output: `build/libs/shopkeeperleaderboard.jar`. Stop the server, back up the plugin data, replace the existing plugin JAR (do not leave two copies), then start the server.

Automated tests cover legacy migration, save/reload, weekly and monthly rollover, timezone boundaries, expired periods after downtime, scoped administrator corrections, sorting and numeric overflow.

In-game acceptance checks (require a running server and client):

- Complete a currency trade and check all three periods in both sort modes.
- Check an empty period, personal rank and navigation with more than 21 sellers.
- Open `/lbadmin`, preview a reduction, cancel it, then confirm it; verify only the selected period changes.
- Check seller/statistic/period resets and custom command amounts.
- Try shift-click, number-key swaps, double-click, offhand swaps and dragging; no menu items should move.
- Revoke admin permission while a menu is open; edits should be refused.
