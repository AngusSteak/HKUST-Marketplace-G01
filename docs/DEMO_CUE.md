# One-page demo cue sheet

Accounts: `admin / admin123`, `alice` / `password1`, `charlie` / `password1`, `diana` / `password1`.

Before you start:

```powershell
Remove-Item -Recurse -Force data
$env:JAVA_HOME="C:\Users\hosze\.jdks\ms-21.0.12.1"
.\mvnw.cmd javafx:run
```

Maximise the window. Keep these open in a browser on a second desktop as backup:

- `docs/screenshots/` (all screens)
- `docs/reports/coverage/index.html` (78.0% branch on measured code; controllers not counted)
- `docs/javadoc/index.html`

## 8-minute path

| Time | Do | Say |
| --- | --- | --- |
| 0:00 | Welcome screen | Three portals, one shared UI; local SQLite, data survives restart. |
| 0:30 | Buyer login: try `charlie`, then `alice` | Wrong-portal check; BCrypt-hashed credentials. |
| 1:00 | Browse: search `charger`, filter Electronics + For Sale + Used, price 200–300 | Search, filters, sort; cards show every required field. |
| 2:00 | Open MacBook Pro Charger → Make Offer 220 | Offer, favourite, follow, report, chat all live on the detail screen. |
| 2:30 | Seller `charlie` → Requests → Counter 240 | Seller sees the offer and counters; buyer is notified. |
| 3:30 | Buyer `alice` → My Deals → Accept Counter → Mark Complete → 5★ | Deal lifecycle, completion, rating prompt. |
| 5:00 | Seller Stats and Reviews | Views/offers/deals/rating; bar and pie charts; reply to review. |
| 6:00 | Admin `admin` → Listing Review → approve/reject | Rejection reason is stored and notifies the seller. |
| 7:00 | Admin Manage Users → deactivate/reactivate | Role checks and account state; soft delete keeps history. |
| 7:30 | Technical close | 568 tests, 0 failures; 78.0% branch (measured code); RBAC in services; synchronized claim; BCrypt; SQLite. |

If a screen fails: close the app and walk `docs/screenshots/` instead. Do not debug live.

## Likely questions

- **Backend?** Desktop client plus service/store layer over local SQLite. No network server.
- **Two buyers claim one giveaway?** `DealLifecycle` is synchronized and re-checks status inside the lock; the first wins.
- **Passwords?** BCrypt cost 12, only hashes stored.
- **Admin deletes a user?** Soft delete: listings hidden, open requests declined, history keeps "deleted user".
- **Coverage?** 78.0% branch on business logic; the JavaFX controllers are excluded from JaCoCo, so the whole-project figure is about 44% and is stated in `docs/reports/coverage.md`.

Known gaps to admit if asked: no date filter in Admin Seller Ratings; no per-thread chat search; auto-return shown by tests, not a live 5-second loan; seeded listings have no photos.
