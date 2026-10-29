# SplitSaathi Architecture Document

## System Philosophy
**SplitSaathi** ("Dosto ka hisaab, saaf saaf") is a shared-expense and accountability manager designed for hostel roommates, college project teams, flatmates, and friends.

### Core Non-Negotiables:
1. **No Money Holding:** We NEVER hold, pool, or transfer funds. We have no wallet or payment gateway. All payments take place peer-to-peer over installed UPI apps.
2. **Integer Paise Storage:** All financial amounts are stored as 64-bit integer paise (`BIGINT` / `Long`). Floating-point arithmetic is prohibited.
3. **Per-Expense Accounting:** Debts and obligations are tracked per expense and per member pair (no automatic cross-expense netting in V1). A read-only "Net View" is available purely for informational reference.
4. **Accountability Flow:** Commitment != Claimed Payment != Confirmed Receipt. Every status change is transparently audited with timestamps and actor IDs.

## Tech Architecture
- **Runtime:** Native Android (Kotlin + Jetpack Compose Material 3), SDK 36.
- **Persistence (Hybrid):**
  - **Local:** SQLite via Android Room with KSP compiler, reactive `Flow` observation, and pre-seeded test accounts for instant zero-config emulator testing.
  - **Cloud:** PostgreSQL schema and migrations for Supabase, with Row Level Security (RLS) on all tables and strict RPC functions for atomic transactions.
- **Design Tokens:** Deep Teal/Emerald (`#0F766E`), Amber (`#F59E0B`), Confirmed Green (`#16A34A`), Disputed Red (`#DC2626`).
- **Android Integrations:**
  - `upi://pay` query declaration in manifest for direct UPI app chooser.
  - Deep link handler for `splitsaathi://join/<code>`.
  - Android Share sheet for invite codes and CSV exports.
