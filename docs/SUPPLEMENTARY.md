# Supplementary notes — HKUST Marketplace (Group 1)

These notes help a grader find the parts of the system that are easy to miss in a short demo.

## What the application is

A JavaFX desktop application for the HKUST campus marketplace, with three portals on one shared visual language: Buyer, Seller and Admin. It runs on JDK 21 with Maven and stores all data in a local SQLite file (`data/marketplace.db`). No server is needed.

Run it:

```powershell
.\mvnw.cmd clean javafx:run
```

Demo accounts: `admin / admin123`, buyers `alice` and `bob` / `password1`, sellers `charlie` and `diana` / `password1`.

## Architecture

- `models/` — `User` (Buyer/Seller/Admin), `Listing` (Sale/Lend/Giveaway), `DealRequest` (offer/borrow/claim), `Deal`, `Message`, `Review`, `Notification`.
- `utils/` — services and stores: `UserStore`, `ListingStore`, `DealLifecycle`, `DealRequestService`, `ChatService`, `SellerService`, `AdminService`, `AccessControl`, `LoanTimer`.
- `persistence/Database` — JDBC connection and schema; each store writes through on every change.
- `controllers/` — JavaFX controllers only render state and collect input. Business rules live in `utils/`.
- `forms/ListingFormState` — validates the create/edit listing form by listing type.

## Design decisions worth asking about

- **Role-based access control is in the service layer.** `AdminService` and `SellerService` call `AccessControl.require(actor, role)` before every change, so a buyer session cannot call seller/admin actions even if the UI is bypassed.
- **Concurrency is handled in the model.** `DealLifecycle` methods are `synchronized` and check the listing status inside the lock. The first claim or accepted offer wins; later ones fail, and accepting one request declines the other open requests.
- **Passwords are hashed with BCrypt, cost 12.** Only `User.passwordHash` is stored. Registration uses `UserStore.checkPasswordStrength`, which returns every broken rule so the form can list them all.
- **Deleting a user or listing is a soft delete.** Listings are hidden and their open requests are declined; user history still resolves the account as "deleted user". This keeps chat, deals and notifications readable without cascade loss.
- **Seller rating is derived** from visible reviews, so hiding a review in Admin → Seller Ratings immediately changes the average.
- **Notification categories** (offer, chat, moderation, rating, account) drive the notification board filters and timestamps.

## Testing and evidence

- 568 JUnit tests, 0 failures. Report: [`reports/unit-tests.md`](reports/unit-tests.md).
- Branch coverage 78.0% on the tested business logic (1,280 of 1,641 branches); line coverage 81.8%. Whole project, with controllers counted as uncovered: 43.9% branch. Report: [`reports/coverage.md`](reports/coverage.md), HTML: [`reports/coverage/index.html`](reports/coverage/index.html).
- JavaDoc: [`javadoc/index.html`](javadoc/index.html).
- Screenshots of every portal screen: [`screenshots/`](screenshots/).
- Demo script with a 15-minute and an 8-minute path: [`DEMO.md`](DEMO.md).

**Coverage scope, stated plainly.** JaCoCo in `pom.xml` excludes the whole `controllers` package
from instrumentation, so no controller branch coverage is measured at all. Those 15 classes are
5,702 lines and hold about 1,272 branches, roughly 44% of all branches in the project. The 65%
requirement is therefore met on the tested business logic only: models, services/stores,
persistence and form state. If the controllers are counted as untouched, whole-project branch
coverage falls to about 44%. We report both numbers rather than only the flattering one; the
controller tests that exist (chat links, seller stats chart, notification board) are not
instrumented, so they contribute nothing to the figure.

## Assumptions and limitations

- This is a single-user desktop demo: there is no network server or session token. `AccessControl` checks are in-process, which is the boundary the course project asks for.
- Photos are copied to `data/photos/` and their relative paths are stored in SQLite. There is no upload service.
- Seed listings have no photos, so their cards show "No photo". A listing created in the demo can add up to six.
- Search, filters and stats run in memory over the loaded rows. That is fine for the seeded data set (tens of listings); a larger data set would need SQL indexes or a full-text index, which the demo does not add.
- No payment step and no push notifications: a deal ends in the app and the notification board is in-app only.

## WoW factors (what to look at in a short demo)

1. **One state machine for every deal.** `models/DealLifecycle` owns offer, borrow, claim, counter,
   accept, decline, return, auto-return and complete. Methods are `synchronized` and re-check the
   listing status *inside* the lock, so two buyers claiming the same giveaway cannot both win, and
   accepting one request automatically declines the others.
2. **Borrow rules in one place, short enough to show live.** `models/LendingRules` sets a 5-second
   minimum, so a loan can be created, reminded and auto-returned during the demo. `utils/LoanTimer`
   ticks once a second, including at start-up, so loans that fell due while the app was closed are
   returned immediately.
3. **Every figure is derived, never cached.** `utils/SellerStatsService` recomputes totals from the
   stores on each view, and seller rating comes from visible reviews. Hiding a review in Admin ->
   Seller Ratings changes the seller's average at once; no totals can drift out of date.
4. **Deletes keep history readable.** Listings and users are soft-deleted, so chats, deal history and
   notification links still open the old record after the listing disappears from Browse.
5. **Notifications point at the thing that caused them.** `models/NotificationRef` stores a target
   type and id, so clicking a notification opens the exact request, deal or review instead of a
   generic list.
6. **Counter-offers cover both kinds of deal.** A seller can counter a price *and* a borrow period;
   both paths go through the same lifecycle and the same deal screen.
7. **Passwords are real hashes, not placeholders.** BCrypt with cost 12; registration returns every
   broken password rule at once rather than the first one.

## Algorithms, data structures and why

| Area | Approach | Cost | Why this choice |
| --- | --- | --- | --- |
| Browse search/filter (`utils/ListingSearch`) | Stream of predicates over the visible listings, then a comparator sort | O(n) filters, O(n log n) sort | n is small; keeping the rules in a plain class makes them unit-testable without JavaFX |
| Keyword match | Lower-cased substring search on title and description | O(len) per field | No tokeniser or index needed at this scale |
| Price sort with mixed types | Priced listings first, unpriced last, ties newest first | O(n log n) | Only Sale listings have a price; the comparator encodes that instead of dropping lend/giveaway rows |
| Deal History filter (`utils/DealHistorySearch`) | Inclusive date bounds, then category, seller and keyword predicates | O(n) | Same shape as Browse, so one test style covers both; inverted ranges fail before filtering |
| Claim arbitration (`models/DealLifecycle`) | Mutual exclusion around check-then-act | O(k) per accept, k = rival requests | Correctness first: the lock is what makes "first claim wins" true, not UI ordering |
| Auto-return (`utils/LoanTimer`) | Single scheduled tick, scan of active loans | O(a) per second, a = active lends | A scan is simpler than per-loan timers and survives a restart, since it reads due dates from the database |
| Seller stats (`utils/SellerStatsService`) | One pass per store, grouping by listing id in a map | O(r + d) | Derived on demand removes cache invalidation entirely, and hidden listings are excluded once at the source |
| Persistence | Write-through stores over JDBC, schema in `db/schema.sql` | one statement per change | Keeps the model authoritative and makes the SQLite file the single source of truth across restarts |

The trade-off is deliberate: the demo optimises for rules that are easy to test and explain, not for
throughput. Every cost above is linear or n log n on tens of rows.

## Keyboard-free demo tip

The app is designed for a 1200×780 or larger window. Maximise it before presenting and reset the demo data first:

```powershell
Remove-Item -Recurse -Force data
```
