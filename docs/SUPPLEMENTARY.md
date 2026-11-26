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

- 503 JUnit tests, 0 failures. Report: [`reports/unit-tests.md`](reports/unit-tests.md).
- Branch coverage 78.7% on the tested business logic; line coverage 83.2%. Report: [`reports/coverage.md`](reports/coverage.md), HTML: [`reports/coverage/index.html`](reports/coverage/index.html).
- JavaDoc: [`javadoc/index.html`](javadoc/index.html).
- Screenshots of every portal screen: [`screenshots/`](screenshots/).
- Demo script with a 15-minute and an 8-minute path: [`DEMO.md`](DEMO.md).

JaCoCo excludes the JavaFX `controllers` package from the coverage figure because it is UI wiring with little branching logic; the excluded classes still have a few smoke tests.

## Assumptions and limitations

- This is a single-user desktop demo: there is no network server or session token. `AccessControl` checks are in-process, which is the boundary the course project asks for.
- Photos are copied to `data/photos/` and their relative paths are stored in SQLite. There is no upload service.
- The borrow dialog picks a date and time, so a 5-second loan cannot be created from the UI. Auto-return is implemented by `LoanTimer` (one-second tick) and covered by `LoanTimerTest`; it is shown by test evidence rather than a live 5-second loan.
- Admin → Seller Ratings filters by seller, score and visibility, but not by date. This is the one filter missing from the brief.
- Chats can be searched across all of a user's threads; there is no per-thread search box.
- Seed listings have no photos, so their cards show "No photo". A listing created in the demo can add up to six.

## Keyboard-free demo tip

The app is designed for a 1200×780 or larger window. Maximise it before presenting and reset the demo data first:

```powershell
Remove-Item -Recurse -Force data
```
