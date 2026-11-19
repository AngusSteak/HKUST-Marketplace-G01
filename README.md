# HKUST Marketplace — JavaFX Demo

A **JavaFX + Maven** desktop demo of the HKUST Campus Marketplace platform.  
All data is in-memory (no database, no network) and resets on restart. The shared user model, credential hashing, portal login/registration flows and seller rating derivation are implemented; the remaining backend integration points are marked with `TODO` comments.

## Prerequisites

1. **JDK 17 or newer** (full JDK, not only JRE)
   - Download: https://adoptium.net/ (Temurin 17 or 21 recommended)
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

## Security State (Demo)

- Passwords are stored only as **BCrypt hashes** (jBCrypt, cost factor 12), produced by `PasswordHasher`. `User.passwordHash` is the only credential field; plaintext passwords are never stored in a `User`, logged, or compared directly. Seeding hashes the demo passwords at startup, buyer/seller registration hashes the chosen password, and every login verifies through `PasswordHasher.verify`.
- Buyer and Seller registration enforces exactly these password-strength rules:
  - The password is not empty.
  - The password is at least 8 characters long.
  - The password contains at least one uppercase letter, at least one lowercase letter, and at least one digit.
- Deactivated accounts (`active = false`) are refused at the login gate of all three portals with an explicit account-deactivated message. The Admin dashboard's activate/deactivate toggle flips the same flag.
- Portal access checks run in the UI layer only. There is no backend in this demo, so API-layer RBAC is still to be built.

> **Demo-only credentials:** `admin / admin123` and the buyer/seller accounts use `password1`. These are hashed at seed time like any other credential, and they exist solely for the demo walkthrough — they bypass registration validation and must not be reused in a real deployment.

## Features Implemented (Demo)

All state lives in `DummyDataService` and is lost when the application closes.

### Shared
- Abstract `User` base with `Buyer` / `Seller` / `Admin` subtypes; each account holds exactly one fixed role, and usernames are unique across all roles
- BCrypt-hashed credentials and a shared login gate that refuses wrong-portal and deactivated accounts on every portal
- Seller ratings derived from non-hidden reviews; "No rating yet" is shown when no visible reviews exist
- Welcome screen with role selection (Buyer / Seller / Admin)
- HKUST branding (logo + colour palette: deep blue `#003974` + gold `#A68137`)
- Modern card-based UI, icons (Ikonli), consistent sidebars & top bars
- In-memory dummy data (no database / no network)

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
    │   └── utils/
    └── resources/
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
Install JDK 17+ from https://adoptium.net/ and **restart the terminal**.  
Make sure the installer option “Add to PATH” was checked.

### Build fails with JavaFX / DISPLAY errors
You need a normal desktop session (not a headless server). On Windows this is usually fine.

### To see the real error
```powershell
.\mvnw.cmd clean javafx:run -e
```
Copy the lines that start with `Exception in thread` or `Caused by`.

## Next Steps (Backend)

1. Replace `DummyDataService` with real repositories / REST clients backed by a database — the current store is in-memory only
2. Keep credentials BCrypt-hashed through `PasswordHasher` when persistence lands; the demo seed hashes the demo passwords at startup
3. Replace the in-memory `SessionManager` with real session tokens and enforce RBAC at the API layer (the current portal checks are demo-only UI gates)
4. Photo upload, chat, auto-return for borrowed items, etc.

## Licence

Educational use — HKUST COMP project skeleton.
