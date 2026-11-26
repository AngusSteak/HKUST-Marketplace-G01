# Coverage report

Command: `mvnw.cmd test` (JaCoCo 0.8.12 report is written during the same run).

- **Branch coverage: 78.7%** (1,194 of 1,517 branches) — above the 65% requirement.
- **Line coverage: 83.2%** (2,919 of 3,509 lines).
- Full HTML report: [`coverage/index.html`](coverage/index.html).

| Package | Line | Branch |
| --- | ---: | ---: |
| `com.hkust.marketplace.models` | 95.9% | 85.5% |
| `com.hkust.marketplace.utils` | 89.2% | 81.3% |
| `com.hkust.marketplace.persistence` | 81.8% | 86.5% |
| `com.hkust.marketplace.forms` | 27.1% | 46.9% |
| `com.hkust.marketplace` (application entry point) | 0.0% | — |
| **Measured total** | **83.2%** | **78.7%** |

## What is measured, and why

JaCoCo excludes `com.hkust.marketplace.controllers` (the JavaFX UI layer, marked in `pom.xml`).
Controllers wire FXML controls to the services and hold almost no branching logic; they are covered by a small set of smoke tests (chat links, seller stats charts, notification board).

The 65% criterion is therefore measured on the testable business logic: models, services/stores, persistence and form state. The HTML report shows only those packages, so the number is reproducible from the committed configuration.

Run `mvnw.cmd test` and open `target/site/jacoco/index.html` to regenerate the same figure.
