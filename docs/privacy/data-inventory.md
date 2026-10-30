# SplitSaathi — Data Inventory & Classification

---

## 1. Data Classification Matrix

| Data Element | Category | Purpose | Storage Location | Retention Period | Sensitivity |
|:---|:---|:---|:---|:---|:---|
| **Display Name** | User Profile | Group member identification | Room SQLite / Postgres `profiles` | Until account deletion or manual edit | Low |
| **Phone Number** | Contact Info | Contact recognition | Room SQLite / Postgres `profiles` | Until account deletion or manual edit | Medium |
| **UPI ID** | Financial Identifier | Peer payment routing | Room SQLite / Postgres `profiles` | Gated by explicit consent; until deletion | High |
| **UPI Consent** | Consent Record | Audit proof of permission | Room SQLite / Postgres `profiles` | Maintained while UPI ID is present | Medium |
| **Expense Details** | Transaction Data | Shared ledger math | Room SQLite / Postgres `expenses` | Indefinite (or until group deletion) | Medium |
| **Payment Claims** | Settlement Record | Proof of debt clearance | Room SQLite / Postgres `payment_records` | Maintained for group accounting fairness | High |
| **Session ID / Auth** | Authentication | Session validation | Android SharedPreferences | Purged upon Sign Out / Account Deletion | High |

## 2. Third-Party Data Sharing
SplitSaathi engages **zero third-party data brokers or advertising providers**.
The only external interaction is user-initiated standard Android intent handover (`Intent.ACTION_VIEW` / `upi://pay`) to whatever UPI application the user selects on their device.
