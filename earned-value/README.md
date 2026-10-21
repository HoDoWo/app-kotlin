# Earned Value – HoDoWo

Status on **2026-10-21** (generated 2026-10-21 17:46 UTC).

| KPI | Name | Formula | Sprint 1 | Cumulative |
|---|---|---|---:|---:|
| **PV** | Planned Value | % planned × BAC | 2,124 € | 2,124 € |
| **EV** | Earned Value | % completed × BAC | 2,245 € | 2,986 € |
| **AC** | Actual Cost | hours spent × rate + services | 2,296 € | 2,938 € |
| **BAC** | Budget at Completion | Sprint 0 estimate | – | 9,879 € |
| **EAC** | Estimate at Completion | BAC / CPI | – | 9,718 € |
| **ETC** | Estimate to Complete | EAC − AC | – | 6,781 € |
| **VAC** | Variance at Completion | BAC − EAC | – | 161 € |
| **CPI** | Cost Performance Index | EV / AC | 0.98 | 1.02 |
| **SPI** | Schedule Performance Index | EV / PV | 1.06 | 1.41 |

## Reading

- CPI 1.02: under budget (the completed work cost less than planned)
- SPI 1.41: ahead of or on schedule
- VAC 161 €: saving money

## Inputs

- BAC: 9,879 € (total cost estimated in Sprint 0), 440 h planned for the whole project.
- % planned: 94.6 h of the Sprint 0 baseline by 2026-10-21 → 21.5%.
- % completed: 133.0 h estimated on the items in Done → 30.2%.
- AC: 142.7 h spent × 20 €/h + 84 € of services.
- Hypothetical costs: the team uses free tiers and pays nothing. Each service counts with the price planned in Sprint 0 from the date it is used.

## Sprint 1 (2026-10-06 → 2026-10-27)

- Items: 43, estimated 127.0 h, completed 100.0 h, spent 113.7 h.
- Open items:
  - #12 Create Project Documentation - Sprint 1 – In Progress (2 h estimated, 0 h spent)
  - #15 Implement guide rating – In Progress (4 h estimated, 0 h spent)
  - #37 Implement Favourites and Recent scanned Page – In Progress (0 h estimated, 0 h spent)
  - #54 Login, register and Google sign-in screens – In Progress (3 h estimated, 15 h spent)
  - #71 Plan Sprint 2 – In Progress (4 h estimated, 0 h spent)
  - #51 Auth and profile – Todo (12 h estimated, 0 h spent)
  - #56 Profile and saved appliances: Firestore schema + UI – Todo (2 h estimated, 0 h spent)

![PV, EV and AC over time](ev.svg)
