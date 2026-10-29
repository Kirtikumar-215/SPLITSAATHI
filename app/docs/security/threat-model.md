# SplitSaathi — Security Threat Model (STRIDE Methodology)

**Target System:** SplitSaathi Mobile Application & Supabase Backend  
**Review Date:** October 29, 2026  
**Status:** Remediated & Hardened  

---

## 1. System Architecture & Trust Boundaries
SplitSaathi involves three primary execution domains:
1. **Client Domain:** Android device sandboxed user space (Room SQLite DB, SharedPreferences, UI Composables).
2. **Network Domain:** Public cellular/Wi-Fi communication over TLS 1.3 to Supabase API endpoints.
3. **Database Domain:** Supabase PostgreSQL instance with Row Level Security (RLS) policies and RPC transaction functions.

---

## 2. STRIDE Threat Analysis & Applied Mitigations

| Threat Category | Potential Attack Vector | Applied Mitigation & Verification |
|:---|:---|:---|
| **Spoofing** | Attacker impersonating another group member to mark payments confirmed or add expenses. | **PostgreSQL RLS & RPC Guards:** `confirm_payment_record` validates `e.paid_by = auth.uid()`. Only the payer can confirm or dispute a claim. Unit & SQL tests verify non-payer rejection. |
| **Tampering** | Man-in-the-middle tampering with HTTP payloads or local SQLite database modifications on rooted device. | **TLS 1.3 HTTPS:** All remote traffic strictly pinned to HTTPS. Schema versioning in Room (`exportSchema = true`) and strict foreign key integrity in Postgres. |
| **Repudiation** | User denies making an expense, altering a split, or receiving a peer payment. | **Immutable Activity Logs:** `activity_logs` table records actor UID, action timestamp, entity type, and prior/new state transitions. |
| **Information Disclosure** | Leakage of private UPI IDs or phone numbers to unauthorized third parties or across groups. | **Gated UPI Access via RLS/RPC:** Profile UPI IDs are only visible to active members within shared groups who have pending payments to that user. Backup rules explicitly exclude session files. |
| **Denial of Service** | Malicious user spamming payment nudges or creating thousands of mock groups. | **Rate-Limiting & Nudge Throttling:** `nudgePayment` enforces a 24-hour rate limit check per debt. RPC membership validation limits group creations. |
| **Elevation of Privilege** | Normal group member escalating themselves to `admin` or accessing other groups' ledgers. | **Strict RLS Subquery Enforcements:** Group members can only read `groups` where `EXISTS (SELECT 1 FROM group_members gm WHERE gm.group_id = groups.id AND gm.user_id = auth.uid())`. Direct role mutations are validated via admin-only RPCs. |

---

## 3. Financial Integrity & Arithmetic Vulnerabilities
- **Floating-point rounding drift:** Solved by mandating **integer paise (`Long`)** across all models, database columns, and mathematical operations.
- **Uneven split truncation:** Solved by deterministic paise distribution in `MoneyUtils.calculateEqualShares` distributing remainder paise sorted by member ID.
- **CSV Formula Injection:** Neutralized in `ReportsScreen.kt` by prepending single quotes to fields starting with `=`, `+`, `-`, `@`, `\t`.
