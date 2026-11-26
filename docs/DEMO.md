# Presentation demo guide

This is the demo script for the COMP 3111 HKUST Marketplace project.
It maps every required subtask to a screen and says what to click and what to say.

---

## 1. What the course actually asks you to demo

The project brief has **27 subtasks**: 10 buyer, 9 seller, 8 admin. There is no separate
demo format in the brief; it says every subtask must be in working form. Expect the TA to
pick items from this list and ask you to show them.

| # | Required subtask | Where it is shown |
|---|------------------|-------------------|
| 1.1 | Buyer registration + validation | Welcome → Buyer → Register here |
| 1.2 | Buyer login + wrong-role check | Buyer login |
| 1.3 | Browse: cards, search, filters, sort | Browse Marketplace |
| 1.4 | Listing detail: photos, fields, rating, chat, report | View Details |
| 1.5 | Offer / borrow / claim + chat + formal offer | Listing detail and Chats |
| 1.6 | My Deals: accept counter, return, complete, auto-return | My Deals |
| 1.7 | Buyer profile | My Profile |
| 1.8 | Buyer notifications | Notifications |
| 1.9 | Deal history + filters | Deal History |
| 1.10 | Rate seller + reviews visible | Mark Complete → Rate Seller |
| 2.1 | Seller registration | Welcome → Seller → Register here |
| 2.2 | Seller login | Seller login |
| 2.3 | Create listing (sale / lend / giveaway, 6 photos) | Create Listing |
| 2.4 | My Listings: edit, delete, mark sold | My Listings |
| 2.5 | Requests: accept, decline, counter, open chat | Requests |
| 2.6 | Seller profile: bio, meetups, password | My Profile |
| 2.7 | Seller notifications | Notifications |
| 2.8 | Seller stats: views, offers, completed, rating, bar + pie | Seller Stats |
| 2.9 | Review reply / flag | Reviews & Feedback |
| 3.1 | Admin login (seeded) | Welcome → Admin |
| 3.2 | Approve / reject listing with reason | Listing Review |
| 3.3 | Manage users: edit, deactivate, delete | Manage Users |
| 3.4 | Admin profile | My Profile |
| 3.5 | All deals + filters | All Deals |
| 3.6 | Admin notifications | Notifications |
| 3.7 | All listings: edit, add on behalf, take down, reports | All Listings |
| 3.8 | Seller ratings: filter, hide | Seller Ratings |

Technical requirements the TA may also ask for:

- RBAC in the service layer, not only the UI.
- Listing view count increments when details open.
- Chat history retained and searchable.
- Concurrent claims: only the first succeeds.
- Hashed credentials, never plaintext.
- JUnit tests, JaCoCo coverage, JavaDoc, Maven build.

---

## 2. Say this first (30 seconds)

> HKUST Marketplace is a JavaFX desktop app with three portals: Buyer, Seller and Admin.
> It uses a local SQLite database, so data survives restarts and no server is needed.
> Buyers browse and filter listings, make offers, borrow requests or giveaway claims, chat
> with sellers, manage deals and rate sellers. Sellers post listings, answer offers, and
> see stats and reviews. Admins approve listings, manage users and listings, handle
> reports, and moderate ratings. Credentials are BCrypt-hashed and every write goes
> through role checks in the service layer.

---

## 3. Before the demo (do this 10 minutes early)

1. Close the app.
2. Reset the demo data so the state is predictable:
   ```powershell
   Remove-Item -Recurse -Force data
   ```
3. Start the app:
   ```powershell
   $env:JAVA_HOME="C:\Users\hosze\.jdks\ms-21.0.12.1"
   .\mvnw.cmd javafx:run
   ```
4. Maximise the window. The layout is designed for 1200×780 or larger.
5. Open these in a browser and keep them on a second desktop:
   - `target/site/jacoco/index.html` (coverage)
   - `target/reports/apidocs/index.html` (JavaDoc)
   - `docs/screenshots/` (backup if the live app fails)
6. Rehearse the script once, then reset `data/` again. Rehearsal changes the data.

Demo accounts (all seeded):

| Role | Username | Password |
|------|----------|----------|
| Admin | `admin` | `admin123` |
| Buyer | `alice`, `bob` | `password1` |
| Seller | `charlie`, `diana` | `password1` |

Seed state after a reset:

- Charlie: **Calculus Textbook** reserved for Alice; **MacBook Pro Charger** live at HK$250.
- Diana: **IKEA Desk Lamp** giveaway live (Alice has a pending claim);
  **Graphing Calculator** lend live (Bob has a pending borrow);
  **Mini Fridge** pending admin review.
- Charlie has one 5-star review from Bob. Alice has an accepted-offer notification.
- Charlie has **no open requests** until you create one in the demo.

---

## 4. The 15-minute demo path

This is one story that touches every portal. Hand off between presenters at the login screens.

### 0:00–1:00 — Welcome and consistency (Person 1)

Show the welcome screen.

> Three portals share the same visual language, cards, sidebar, notification board and
> profile pattern. Data is stored locally in SQLite.

### 1:00–2:30 — Buyer registration and validation (Person 1)

1. **Enter as Buyer** → **Register here**.
2. Submit with username `alice` and any password: show the duplicate-username error.
3. Submit with a weak password (`abc`): show the full list of broken password rules.
4. Register `demo_buyer` / `Demo User` / `Password1!` (confirm).
5. Point out the success message and that the password is hashed (never stored as text).

If short on time: show one error and skip the successful registration; use `alice`.

### 2:30–3:00 — Login and wrong-portal check (Person 1)

1. Log out, open Buyer login.
2. Try `charlie` / `password1`: show "wrong portal" error.
3. Log in as `alice` / `password1`.

### 3:00–4:30 — Browse, search, filter, sort (Person 1)

1. **Browse Marketplace**. Point at a card: title, seller, category, type, price,
   condition, meetup, posted date, status.
2. Search `charger`.
3. Filter Category = Electronics, Type = For Sale, Condition = Used.
4. Set a price range 200–300 and sort Price: low to high, then Newest.
5. Clear the filters.

### 4:30–6:00 — Listing detail, favourite, follow, report (Person 1)

1. Open **MacBook Pro Charger 61W** → **View Details**.
2. Point out: full description, all fields, seller name and average rating, availability,
   **Chat**, **Report Listing**, **Favourite**, **Follow Seller**.
3. Click **Favourite** and **Follow Seller** (labels change).
4. Open **Report Listing**, type "Price looks suspicious", submit. Do not confirm a modal
   that removes the listing; this only files a report for the admin.
5. Explain that a report appears under Admin → All Listings → Reports.

### 6:00–8:00 — Offer → counter → accept (Person 1 → Person 2)

1. As Alice, click **Make Offer**, enter `220`, send. Confirmation appears and the request
   is in **My Deals**.
2. Log out. Log in as **Seller** `charlie` / `password1`.
3. **Requests** shows Alice's HK$220 offer on the charger.
4. Click **Counter**, enter `240`, confirm.
5. Log out, log back in as `alice`.
6. **My Deals** → the countered request shows **Accept Counter** and **Decline**.
   Click **Accept Counter**.
7. Point out the listing is now **Reserved** on the seller side and the deal is accepted.

This covers 1.5, 1.6 (start), 2.5 and notifications.

### 8:00–9:30 — Complete the deal, rate the seller (Person 1)

1. As Alice, **My Deals** → the accepted charger → **Mark Complete**.
2. The prompt asks to rate the seller: choose 5 stars, write "Fast and friendly", submit.
3. Open **My Reviews**: the review is listed.
4. Open **Deal History**: the completed deal is there; demo the date/category/seller filters.
5. Open the charger detail again: the seller rating and review now include this deal.

### 9:30–11:00 — Seller stats, reviews, notifications (Person 2)

1. Log in as `charlie`.
2. **Seller Stats**: views, offers received, completed deals, average rating; the bar chart
   shows views/offers per listing, the pie chart shows completed deals by type (appears
   now that the deal is complete). Click a table row / bar to filter.
3. **Reviews & Feedback**: show Bob's 5-star review, reply "Thanks!", save. Explain the
   buyer gets a notification and can flag a review.
4. **Notifications**: show the new request, approval, and review notifications with
   timestamps and categories.

### 11:00–12:30 — Create a listing, admin approves (Person 2 → Person 3)

1. As `charlie`, **Create Listing**.
2. Choose **For Sale** first; point out the form changes by type (price vs lending duration).
3. Fill: Item name `Demo Bluetooth Speaker`, Category Electronics, Condition Like New,
   Description, Price `100`, Meetup Academic Building.
4. Optional: **Add photos**, pick one image (up to 6), show the preview and remove button.
5. **Submit for Review** → confirmation. **My Listings** shows **PENDING_REVIEW**.
6. Log out. Log in as `admin` / `admin123`.
7. **Listing Review** has the new speaker and the seeded Mini Fridge.
8. Reject **Mini Fridge** with reason `Please add a clearer photo` (it is stored and the
   seller is notified). Approve **Demo Bluetooth Speaker**.
9. Log back in as `charlie` → **Notifications** shows approved; **My Listings** shows Live;
   the buyer's Browse now shows the speaker.

### 12:30–14:00 — Admin users, deals, ratings, reports (Person 3)

1. **Manage Users**: search `demo_buyer`; **Edit** full name; **Deactivate** (login is then
   refused); **Reactivate**. Click **Delete** on `demo_buyer` to show the soft-delete dialog
   (listings/requests/deals/chats that would close); confirm. The account now reads
   "deleted user" in history.
2. **All Deals**: filter by type, status and date; show the completed charger deal.
3. **Seller Ratings**: filter by seller and score; **Hide** a review and show it stops
   counting towards the average; **Unhide**.
4. **All Listings** → **Reports** on the charger: show Alice's report; dismiss with a note.
   (Alternative: **Take down** a different live listing with a reason and show the seller's
   notification.)

### 14:00–15:00 — Technical evidence (any presenter)

Say these, and show the pages you prepared:

- **568 JUnit tests pass**; JaCoCo report open; 78.0% branch coverage on business logic (81.8% lines), models 85.7% branches, services 81.5%, persistence 86.5%.
- **JavaDoc** builds clean (`mvnw javadoc:javadoc`).
- **RBAC**: `AccessControl.require` runs inside `AdminService` and `SellerService` before
  every change, so a buyer session cannot call seller/admin actions even if the UI is bypassed.
- **Concurrency**: `DealLifecycle` is synchronized; accepting one request declines the
  others, and a second acceptance on the same listing is rejected (unit tested).
- **View counts**: each listing-detail open calls `ListingStore.recordView`, which is what
  the seller's stats chart reads.
- **Chat search**: Chats → search box searches all of the user's messages; history is
  persisted in SQLite.
- **Security**: BCrypt cost 12; the database stores only hashes.
- **Persistence**: all stores write through to `data/marketplace.db`; restart the app to
  show data survives.

### 15:00 — Q&A

Use section 7.

---

## 5. If you only have 8 minutes (core path)

1. Buyer login (30s) → Browse + filters (1 min) → charger detail (30s).
2. Make offer HK$220 (30s).
3. Seller login → Requests → Counter HK$240 (1 min).
4. Buyer login → My Deals → Accept Counter → Mark Complete → Rate 5★ (1.5 min).
5. Seller Stats (pie now visible) + Reviews reply (1 min).
6. Admin login → approve the speaker (create it first if time) + Manage Users (1 min).
7. One technical sentence: 568 tests, RBAC in services, BCrypt, SQLite (30s).

---

## 6. Other subtasks to show if the TA asks

- **Borrow**: `bob` → Browse → Graphing Calculator → **Request Borrow**. Show start/end,
  the listing's 90-day limit, and the pending request in My Deals. After the seller
  accepts, My Deals shows the due date and **Return Item**. Auto-return is implemented by
  `LoanTimer`, which ticks every second and returns overdue loans (covered by
  `LoanTimerTest`); it is hard to show live because the borrow dialog works in minutes.
- **Claim**: `alice` → IKEA Desk Lamp → **Claim Item**. Only the first claim wins; the
  lifecycle is synchronized and later claims are rejected.
- **Formal offer in chat**: Chats → open a thread → **Make Offer** (or request a lending
  period). This creates a request linked to the listing.
- **Chat search**: Chats → type a keyword in "Search all my chats..." and choose a filter.
- **Seller edit / delete / mark sold**: My Listings. Editing is blocked while offers are
  open; delete is a soft delete that declines open requests and notifies buyers.
- **Giveaway lifecycle**: seller accepts a claim → listing becomes Given.
- **Profile**: both buyer and seller can change name/password; seller also bio and meetup
  locations. Show a wrong current password and a confirmation mismatch.
- **Admin add listing on behalf**: All Listings → `+ Add listing on behalf of a seller`.
- **Admin add user**: Manage Users → `+ Add user`.

---

## 7. Likely TA questions

**"Where is the backend?"**
> The app is a desktop client with a local SQLite database. The service classes
> (`SellerService`, `AdminService`, `ChatService`, stores) are the backend layer; the
> JavaFX controllers only render and collect input. There is no network server.

**"How is RBAC enforced?"**
> Every service method takes the acting user and calls `AccessControl.require(actor, role)`
> first. Wrong role, inactive or deleted accounts throw `SecurityException` before any
> change. Unit tests cover forbidden actors.

**"How do you stop two buyers claiming the same giveaway?"**
> `DealLifecycle` methods are synchronized and check the listing status inside the lock.
> The first claim reserves it; the next sees a non-live listing and fails. Accepting one
> request also declines the other open requests.

**"Are passwords really hashed?"**
> Yes, BCrypt with cost factor 12 via `PasswordHasher`. Only the hash is stored, never the
> plaintext, and login verifies with `BCrypt.checkpw`. Demo passwords are hashed at seed
> time too.

**"What happens when an admin deletes a user?"**
> It is a soft delete: open listings are taken down, open requests declined, active deals
> cancelled, affected users notified, profile fields cleared, and an audit entry written.
> History still resolves the account and labels it "deleted user". The brief allows this
> recommended option.

**"What is tested?"**
> 568 JUnit tests across models, stores, services and the database. JaCoCo report shows
> 96% line coverage for models, 89% for services/utils, 82% for persistence. Controllers
> are covered lightly because they are UI.

**"What is not finished?"**
> Be honest about the two gaps in section 8.

---

## 8. Known gaps: do not get caught

1. **Admin Seller Ratings has no date filter.** It filters by seller, rating score and
   visibility. The brief asks for user, score and date. If asked, say the date filter is
   the one missing filter.
2. **Seller stats pie chart is empty until a deal is completed.** The bar chart always
   shows once the seller has listings. Complete the demo deal before opening Stats.
3. **The welcome footer now says "COMP 3111 F2026 · HKUST Marketplace · Local SQLite storage".**
   The old "Skeleton / No backend" label was removed before the demo.
4. **Auto-return is not shown live.** The borrow dialog uses date + HH:mm, so a 5-second
   loan cannot be created from the UI; the timer and its tests are the evidence.
5. **Seeded listings have no photos**, so their cards show "No photo". Add a photo in the
   Create Listing demo if photos matter to the TA.
6. **No search inside a single chat thread.** The search covers all of the user's chats
   from the Chats screen. The brief says chat history must be searchable, which this meets.

---

## 9. If the live demo breaks

- Keep the app closed, open `docs/screenshots/` and walk through the 29 saved screens.
- If the database is corrupted, close the app, delete `data/`, relaunch.
- If the build fails, check `JAVA_HOME` points at `C:\Users\hosze\.jdks\ms-21.0.12.1`
  and run `.\mvnw.cmd -q compile` once before the demo.
- The final ZIP and tag are `v1.1.0`; do not edit the repo during the demo.
