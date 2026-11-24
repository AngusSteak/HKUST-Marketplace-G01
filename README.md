# HKUST Marketplace — JavaFX Demo

A **JavaFX + Maven** desktop demo of the HKUST Campus Marketplace platform.  
Data is stored in a local **SQLite** database (`data/marketplace.db`, kept across restarts; see [Database](#database)). The shared user model, credential hashing, portal login/registration flows and seller rating derivation are implemented; the remaining backend integration points are marked with `TODO` comments.

## Prerequisites

1. **JDK 21 or newer** (full JDK, not only JRE). The course requires JDK 21+.
   - Download: https://adoptium.net/ (Temurin 21 recommended)
   - After install, open a **new** PowerShell and check:
     ```powershell
     java -version
     javac -version
     ```
   - Both commands must work. If `javac` is missing, you only have a JRE — install the JDK.

2. **Maven is optional** — this project includes the **Maven Wrapper** (`mvnw.cmd` on Windows).

## How to Run (Windows)

Open **PowerShell** or **Command Prompt** in the project folder  
(`...\HKUST-Marketplace\HKUST-Marketplace`):

```powershell
.\mvnw.cmd clean javafx:run
```

First run downloads Maven automatically (needs internet). Then the app window should open.

### Alternative: if you already installed Maven and added it to PATH

```powershell
mvn clean javafx:run
```

### Run from IntelliJ IDEA

1. Open the project folder as a Maven project
2. Wait for dependencies to download
3. Open `MarketplaceApp.java` → right-click → **Run**

### Run from VS Code

1. Install **Extension Pack for Java**
2. Open the folder
3. Run `MarketplaceApp.java`

## Demo Credentials

| Role   | Username  | Password   |
|--------|-----------|------------|
| Admin  | `admin`   | `admin123` |
| Buyer  | `alice`   | `password1`|
| Buyer  | `bob`     | `password1`|
| Seller | `charlie` | `password1`|
| Seller | `diana`   | `password1`|

## Database

The app uses a local **SQLite** file database through `sqlite-jdbc` (a normal Maven dependency, so there is nothing to install or configure).

- **File:** `data/marketplace.db`, relative to the folder you run the app from. It is created and filled with the demo data on first run. `data/` is git-ignored and must not be included in the submission ZIP.
- **Schema:** [`src/main/resources/db/schema.sql`](src/main/resources/db/schema.sql), applied on every start (`CREATE TABLE IF NOT EXISTS`). Open the file with [DB Browser for SQLite](https://sqlitebrowser.org/) or `sqlite3` to inspect it.
- **How it works:** each store (`users()`, `listings()`, ...) keeps its objects in memory, writes every change to the database straight away, and reads everything back once at startup (`persistence/Database`, `utils/DummyDataService`).
- **What is saved:** everything — users, listings (with their photo paths), deal requests and deals, favourites and follows, chat threads and messages, notifications, reviews, listing reports and the admin audit log. Data is **kept across restarts**.
- **Reset to the demo data:** quit the app and delete the `data/` folder; the next launch creates a fresh database with the demo data. Alternatively run `MarketplaceApp` once from IntelliJ with the VM option `-Dmarketplace.resetDb=true` (Run → Edit Configurations → Modify options → Add VM options).
- **Changing the schema:** `CREATE TABLE IF NOT EXISTS` does not alter existing tables, so after changing a table in `schema.sql`, delete `data/` (or reset as above) to recreate it.
- **One-time reset for the hidden flag:** listings now persist a `deleted` visibility column (`0` = visible, `1` = hidden). A database created before this column existed will not gain it from `CREATE TABLE IF NOT EXISTS`, so delete `data/` once (or reset as above) after pulling this change. The first launch afterwards recreates the schema with the column; no further reset is needed and data is kept across restarts as usual.
- **Tests** use a private in-memory SQLite database per `new DummyDataService()`, so they never touch `data/`.

**Team rule:** screens never change a model object and skip the store. Every change goes through a store method, e.g. `listings().update(listing)` after changing a listing or `users().save(user)` after changing an account, so it is written to the database. Requests and deals change only through `deals().lifecycle()`, which saves them (and the listing status they set) itself. **Deleting a listing is a soft delete** (`SellerService.deleteListing`): it declines any pending or countered offers, notifies their buyers, and sets the `deleted` flag via `listings().update(listing)` — the row is kept so chat, deal history and notification links still resolve it. Hidden listings are omitted from Browse/Search, favourites, My Listings, admin lists and seller counts; never remove a listing row from a seller screen.

**Role checks (#60):** admin-only and seller-only actions live in `AdminService` and `SellerService`. Each method takes the acting user (`SessionManager.getCurrentUser()`) and calls `AccessControl.require(actor, role)` first, which throws `SecurityException` for the wrong role or an inactive account. New role-specific actions should follow the same pattern (`ChatService` already does for chat participants), so the check holds even if a screen shows a button it shouldn't.

## Tests and Coverage

```powershell
.\mvnw.cmd test
```

- JUnit 5 tests cover the models and services (stores, deal lifecycle, chat, reviews, notifications, admin actions, profile, seller stats) plus the database layer. JavaFX controllers are covered lightly, since most of their work is rendering; a few controller tests cover chat links, seller stats charts and the notification board.
- Tests use an in-memory SQLite database (`Database.inMemory()` or a private `DummyDataService`), so they never create or read `data/marketplace.db`.
- JaCoCo writes the coverage report to `target/site/jacoco/index.html` (CSV: `target/site/jacoco/jacoco.csv`) during the same `test` run.
- Latest run: **489 tests, 0 failures**. Line coverage: models **96%**, utils/services **91%**, persistence **83%**, overall **44%** (the lightly covered controllers pull the total down).

## Security State (Demo)

- Passwords are stored only as **BCrypt hashes** (jBCrypt, cost factor 12), produced by `PasswordHasher`. `User.passwordHash` is the only credential field; plaintext passwords are never stored in a `User`, logged, or compared directly. Seeding hashes the demo passwords at startup, buyer/seller registration hashes the chosen password, and every login verifies through `PasswordHasher.verify`.
- Buyer and Seller registration enforces the full UC-1.1 A3 password rules through `UserStore.checkPasswordStrength(password, username)`:
  - 8 to 64 characters.
  - At least one uppercase letter, one lowercase letter, one digit and one special character.
  - No spaces.
  - Not equal to the username (case-insensitive).
  - The check returns a set of `PasswordRule` values naming **every** broken rule, so the form can tell the user what to fix instead of failing on the first problem.
- Deactivated accounts (`active = false`) are refused at the login gate of all three portals with an explicit account-deactivated message. The Admin dashboard's activate/deactivate toggle flips the same flag.
- Portal access checks run in the UI layer only. There is no backend in this demo, so API-layer RBAC is still to be built.

> **Demo-only credentials:** `admin / admin123` and the buyer/seller accounts use `password1`. These are hashed at seed time like any other credential, and they exist solely for the demo walkthrough — they bypass registration validation and must not be reused in a real deployment.

## Features Implemented (Demo)

All state is reached through `DummyDataService` and its per-area stores, backed by SQLite (see [Database](#database)).

### Shared
- Abstract `User` base with `Buyer` / `Seller` / `Admin` subtypes; each account holds exactly one fixed role, and usernames are unique across all roles
- BCrypt-hashed credentials and a shared login gate that refuses wrong-portal and deactivated accounts on every portal
- Seller ratings derived from non-hidden reviews; "No rating yet" is shown when no visible reviews exist
- Welcome screen with role selection (Buyer / Seller / Admin)
- HKUST branding (logo + colour palette: deep blue `#003974` + gold `#A68137`)
- Modern card-based UI, icons (Ikonli), consistent sidebars & top bars
- Local SQLite database with seeded demo data (no server, no network)

### Buyer Portal
- Registration, Login, Browse Marketplace, Make Offer / Borrow / Claim
- My Deals, Deal History, Favourites (placeholder), Notifications, Profile

### Seller Portal
- Registration, Login, Create Listing, My Listings
- Seller Stats (charts), Reviews & Feedback, Notifications, Profile

### Admin Portal
- Login only (seeded), Listing Review (approve/reject)
- Manage Users, All Listings, All Deals, Seller Ratings, Notifications, Profile

## Project Structure

```
HKUST-Marketplace/
├── mvnw / mvnw.cmd          ← Maven Wrapper (use these!)
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/hkust/marketplace/
    │   ├── MarketplaceApp.java
    │   ├── controllers/
    │   ├── models/
    │   ├── persistence/     ← SQLite connection (Database)
    │   └── utils/           ← per-area stores, DummyDataService
    └── resources/
        ├── db/schema.sql    ← database schema
        ├── fxml/
        ├── css/styles.css
        └── images/hkust_logo.png
```

## Troubleshooting

### `mvn` is not recognized
Use the wrapper instead:
```powershell
.\mvnw.cmd clean javafx:run
```

### `java` / `javac` not recognized
Install JDK 21+ from https://adoptium.net/ and **restart the terminal**.  
Make sure the installer option “Add to PATH” was checked.

### Build fails with JavaFX / DISPLAY errors
You need a normal desktop session (not a headless server). On Windows this is usually fine.

### To see the real error
```powershell
.\mvnw.cmd clean javafx:run -e
```
Copy the lines that start with `Exception in thread` or `Caused by`.

## Next Steps (Backend)

1. Persist the remaining stores to SQLite (#56, #57, #58), then keep data across restarts by default (#59)
2. Credentials stay BCrypt-hashed through `PasswordHasher`; the database only ever holds the hash
3. Replace the in-memory `SessionManager` with real session tokens and enforce RBAC at the API layer (the current portal checks are demo-only UI gates)
4. Chat, auto-return for borrowed items, etc. (Photo choosing is wired into the seller Create Listing form: files are copied to a local `data/photos/` folder and their relative paths are persisted in the `listing_photos` table — a demo stand-in for real upload storage.)

## Licence

Educational use — HKUST COMP project skeleton.
