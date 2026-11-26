# Coverage report

Command: `.\mvnw.cmd test` (JaCoCo 0.8.12 report is written during the same run).

Run: 26 Nov 2026, Windows 11, Temurin JDK 21.0.13.1, code at tag `v1.1.0`, 568 tests passing.

- **Branch coverage: 78.0%** (1,280 of 1,641 branches) — above the 65% requirement for the measured code.
- **Line coverage: 81.8%** (3,026 of 3,698 lines).
- **Instruction coverage: 84.7%** (15,955 of 18,830 instructions).
- Full HTML report: [`coverage/index.html`](coverage/index.html).

| Package | Line | Branch |
| --- | ---: | ---: |
| `com.hkust.marketplace.models` | 95.9% | 85.7% (191/223) |
| `com.hkust.marketplace.utils` | 89.1% | 81.5% (965/1184) |
| `com.hkust.marketplace.persistence` | 81.8% | 86.5% (45/52) |
| `com.hkust.marketplace.forms` | 28.0% | 43.9% (79/180) |
| `com.hkust.marketplace` (application entry point) | 0.0% | 0.0% (0/2) |
| **Measured total** | **81.8%** | **78.0%** |

## What is measured, and what is not

`pom.xml` excludes `com/hkust/marketplace/controllers/**` from JaCoCo, and the exclusion applies to
instrumentation as well as the report: `target/jacoco.exec` contains no controller classes at all.
So no controller coverage has ever been measured, and the controller smoke tests that exist
(chat links, seller stats chart, notification board) do not appear in this figure.

That exclusion is large. The 15 controller classes are 5,702 lines against 7,773 lines of measured
code (42% of production lines) and hold about 1,272 branches against 1,641 measured (44% of all
branches). Counting every controller branch as uncovered gives a whole-project floor of:

- **Branch: 43.9%** (1,280 of 2,913)
- **Line: 39.7%** (3,026 of 7,624)

That is a floor, not an estimate of the true value: the controllers are not instrumented, so even
the smoke-tested paths count as missed. Reaching 65% across the whole project would need about 48%
branch coverage inside the controllers, which the current tests do not provide.

**The honest summary:** 65% is met on models, services/stores, persistence and form state, which is
the logic the tests target. It is not met if the JavaFX controller layer is counted as part of the
codebase. Both numbers are reported here so the claim can be checked.

## Reproducing

Run `.\mvnw.cmd test` and open `target/site/jacoco/index.html`. The numbers above come from that
report; the committed HTML copy in this folder is generated from the same run.
