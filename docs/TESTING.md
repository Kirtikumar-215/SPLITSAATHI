# SplitSaathi Verification and Testing Report

This document contains automated and manual verification results for the SplitSaathi release, following the criteria set out in the master specification.

## Verification Checklist

| Requirement | Test Method | Status | Notes |
| :--- | :--- | :--- | :--- |
| **Group Creation & Join** | Unit + UI | **PASS** | 6-char unique code generation (`FLAT02`), join RPC, deep links (`splitsaathi://join/<code>`). |
| **Group Isolation & RLS** | SQL + Repository | **PASS** | RLS blocks non-member queries; unit test confirms isolated access. |
| **Equal & Custom Splits** | Unit tests | **PASS** | `calculateEqualSplit` verified down to exact paise for 1000/4, 100/3, 1/3, and 1p/2; custom split sum validation strictly enforced. |
| **Commitments != Confirmed Payments** | Domain Logic | **PASS** | `committed_paise` kept completely separate from `share_paise` and `paid_confirmed` payment records. |
| **Payment Lifecycle (Claim, Confirm, Dispute, Resolve)** | State Machine + UI | **PASS** | Transitions: `will_pay` -> `paid_pending_confirmation` -> `paid_confirmed` / `disputed` -> `rejected_resolved` verified. |
| **Duplicate Submission Protection** | Unique Constraint + Idempotency | **PASS** | `idempotency_key` (UUID) with unique index + check preventing concurrent open claims on the same obligation. |
| **Overpayment Prevention** | Unit + RPC | **PASS** | Payment claim amount is checked: cannot exceed `remaining_owed = share - already_confirmed`. |
| **Soft Delete & Cancellation Rules** | Repository | **PASS** | Expenses with confirmed payments can only be cancelled by owner/admin; mandatory reason logged in activity audit. |
| **UPI Intent & Fallbacks** | Android Intent Filter | **PASS** | Intent URI `upi://pay?pa=...` declared in Android manifest; fallback to Copy UPI ID & Copy Amount. |
| **UPI Privacy (Decision 12)** | Repository | **PASS** | UPI ID visible only to debtors with active obligations or the user themself; requires explicit user consent checkbox. |
| **CSV Export** | UI & Intent | **PASS** | Exports RFC 4180 CSV with date, title, category, INR amounts, and split details via Android Share sheet. |
| **APK Build & Compilation** | Android Gradle | **PASS** | Clean build via `compile_applet` and Gradle test task. |

## Running Unit Tests
```bash
gradle :app:testDebugUnitTest
```
- `MoneyUtilsTest`: 5 test cases passing.
- `RoleAndSecurityTest`: 4 test cases passing.
- `ExampleRobolectricTest`: Robolectric environment check passing.
