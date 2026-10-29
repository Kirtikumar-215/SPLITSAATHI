# SplitSaathi — Dosto Ka Hisaab, Saaf Saaf

SplitSaathi is an Android shared-expense manager for hostel students, roommates, college project teams, flatmates, and friends.

> **Legal Disclaimer:** SplitSaathi manages expense records, contributions, and payment accountability. We NEVER hold, pool, or transfer money. Users pay each other directly via their own personal UPI apps. All amounts are internal accounting records, not bank balances.

---

## Key Features

- **Integer Paise Money Engine:** All amounts stored strictly as integer paise down to the exact paisa (e.g. ₹100 divided 3 ways = ₹33.34, ₹33.33, ₹33.33). Indian numbering system formatting (`₹1,25,000.00`).
- **Equal & Custom Splits:** Live preview of participant shares before saving; custom shares validated to match the total exactly.
- **Pay Directly via UPI:** Launches external UPI chooser (`upi://pay?pa=...&am=...&cu=INR&tn=...`) with fallback to copy UPI ID and amount.
- **Post-Return Confirmation Sheet:** "Did you complete the payment?" with "Yes, I paid" (records pending claim + optional UPI/UTR reference), "Not yet", and "I'll do it later".
- **Accountability & Status Workflow:**
  - `Will Pay` -> `Waiting for Confirmation` -> `Confirmed Paid`
  - `Disputed` (with mandatory reason) -> Admin / Recipient resolution
  - In-app **Nudge** (max 1 per 24 hours per payment)
- **Who Owes Whom & Net View:**
  - Per-expense, per-pair obligations as the primary ground truth.
  - Read-only informational Net View toggle (sum of A->B minus B->A).
- **UPI Privacy:** UPI ID is only visible to group members who currently owe that person, and requires explicit user consent.
- **Invite Links & Deep Links:** 6-character unique invite code (`FLAT02`) + deep linking (`splitsaathi://join/<code>`) + Android Share sheet.
- **Activity Feed & Notification Center:** In-app updates for new expenses, payment claims, confirmations, disputes, and nudges.
- **Reports & CSV Export:** Category breakdown with percentage progress bars, spending metrics, and 1-tap CSV export.

---

## Project Structure

```
├── app/src/main/
│   ├── AndroidManifest.xml          # UPI queries & deep link filters
│   └── java/com/example/
│       ├── MainActivity.kt          # App entry point & deep link parser
│       ├── data/
│       │   ├── local/               # Room DAOs, Database, Seed data
│       │   ├── model/               # Entities, Enums, Domain models
│       │   └── repository/          # SplitSaathiRepository (RPC & Business logic)
│       ├── ui/
│       │   ├── components/          # TopBar, StatusChip, AvatarView, Banner
│       │   ├── screens/             # Onboarding, Auth, Groups, Detail, Pay, Reports
│       │   └── theme/               # Emerald / Amber fintech M3 theme
│       └── util/
│           └── MoneyUtils.kt        # Integer paise math & Indian currency format
├── supabase/
│   ├── migrations/                  # Complete PostgreSQL RLS & RPC schema
│   ├── seed.test.sql                # Test seed data
│   └── tests/rls_test.sql           # RLS isolation verification
└── docs/                            # ARCHITECTURE.md, DATABASE.md, TESTING.md, BUILD_APK.md
```

---

## Verification & Testing

Run the automated test suite:
```bash
gradle :app:testDebugUnitTest
```
Build the installable APK:
```bash
gradle assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`
