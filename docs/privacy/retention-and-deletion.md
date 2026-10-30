# SplitSaathi — Data Retention, Anonymization & Deletion Policy

---

## 1. Principles of Data Retention
In shared ledger systems (such as SplitSaathi), unconditional deletion of financial transactions can corrupt the mutual accounting balances of remaining participants (e.g., deleting an expense where three other people already paid their shares creates an irreconcilable financial discrepancy).

To satisfy both individual privacy rights (under DPDP / GDPR) and mutual contract/accounting integrity:
1. **Personal Identity Deletion (Immediate):** Upon user request, personal identifying attributes (Full Name, Phone Number, UPI ID, Session Tokens) are purged or anonymized.
2. **Mutual Ledger Retention (Anonymized):** The immutable mathematical transaction history (amount in paise, date, category, split share proportions) is retained with the member reference reassigned to an anonymized placeholder: `"Deactivated Member"`.

## 2. In-App Deletion Workflow
- Located at: **Profile Screen -> Delete Account & Reset Data**.
- Action:
  1. Displays clear confirmation dialog detailing the retention of anonymized accounting records.
  2. Executes `repository.deleteAccountAndClearData()`.
  3. Replaces user profile in local database with `displayName = "Deactivated Member"`, `phone = null`, `upiId = null`, `upiConsent = false`.
  4. Calls `UserSessionManager.clearSession()`.
  5. Navigates user back to clean First-Launch Onboarding.
