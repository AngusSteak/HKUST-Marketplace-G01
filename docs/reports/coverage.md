# Coverage report

Command: `mvnw.cmd test` (the JaCoCo 0.8.12 report is written during the same run).

- **Branch coverage: 76.2%** (2,220 of 2,915 branches), above the 65% requirement. Every package is measured, including the JavaFX controllers.
- **Line coverage: 84.9%** (6,479 of 7,627 lines).
- Without the controllers (business logic only), branch coverage is 89.0%.
- Full HTML report: [`coverage/index.html`](coverage/index.html).

| Package | Line | Branch |
| --- | ---: | ---: |
| `com.hkust.marketplace` | 0.0% | 0.0% |
| `com.hkust.marketplace.controllers` | 100.0% | — |
| `com.hkust.marketplace.controllers.admin` | 83.5% | 60.1% |
| `com.hkust.marketplace.controllers.buyer` | 82.3% | 59.4% |
| `com.hkust.marketplace.controllers.chat` | 60.1% | 65.4% |
| `com.hkust.marketplace.controllers.common` | 70.1% | 34.4% |
| `com.hkust.marketplace.controllers.notifications` | 93.8% | 66.7% |
| `com.hkust.marketplace.controllers.seller` | 73.2% | 61.0% |
| `com.hkust.marketplace.forms` | 75.3% | 71.1% |
| `com.hkust.marketplace.models` | 97.4% | 90.1% |
| `com.hkust.marketplace.persistence` | 81.8% | 86.5% |
| `com.hkust.marketplace.utils` | 96.5% | 91.8% |
| **Total** | **84.9%** | **76.2%** |

## How the screens are tested

The screen tests (`src/test/java/com/hkust/marketplace/controllers`) open the buyer, seller and admin portals, the login and registration screens, and the shared chat view on a private in-memory copy of the demo data, then type, choose and click the way a user would. Pop-up dialogs are answered by the test. They need a display; without one they are skipped.

Run `mvnw.cmd test` and open `target/site/jacoco/index.html` to regenerate the same figures.
