# HKUST Marketplace — JavaFX Demo

A **JavaFX + Maven** desktop demo of the HKUST Campus Marketplace platform.  
Data is stored in a local **SQLite** database (`data/marketplace.db`; see [Database](#database)). For now it is recreated with the demo data on every launch, until all stores are saved (#59). The shared user model, credential hashing, portal login/registration flows and seller rating derivation are implemented; the remaining backend integration points are marked with `TODO` comments.

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
- **Reset to the demo data:** delete the `data/` folder.
- **Migration status:** users and listings (#55), and deal requests, deals, favourites and follows (#56) are saved now. Chats, notifications, reviews, reports and the audit log follow in #57 and #58. Until then the database is **recreated on every launch** so all stores stay consistent. To keep data across restarts already (only what is saved so far), run `MarketplaceApp` from IntelliJ with the VM option `-Dmarketplace.resetDb=false` (Run → Edit Configurations → Modify options → Add VM options). #59 makes keeping data the default.
- **Tests** use a private in-memory SQLite database per `new DummyDataService()`, so they never touch `data/`.

**Team rule:** screens never change a model object and skip the store. Every change goes through a store method, e.g. `listings().update(listing)` after changing a listing or `users().save(user)` after changing an account, so it is written to the database. Requests and deals change only through `deals().lifecycle()`, which saves them (and the listing status they set) itself. A listing with requests or deals cannot be deleted; take it down by status instead.

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
4. Photo upload, chat, auto-return for borrowed items, etc.

## Licence

Educational use — HKUST COMP project skeleton.
