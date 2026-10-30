# SplitSaathi — Security Controls Specification

---

## 1. Authentication & Session Security
- **Supabase Anonymous Auth:** Ephemeral or anonymous session tokens managed securely via Android sandboxed preferences (`splitsaathi_user_session.xml`).
- **Backup Rule Hardening:** In `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/data_extraction_rules.xml`, `splitsaathi_user_session.xml` is explicitly excluded from Google Cloud Auto-Backup, ensuring session tokens are never backed up to unencrypted cloud drives.

## 2. Network & Transport Security
- **Strict HTTPS / TLS 1.3:** Enforced for all Supabase API and database calls.
- **Cleartext Traffic Disabled:** Android Manifest default prevents cleartext HTTP communication.

## 3. Database Security & Row Level Security (RLS)
- **Zero-Trust Default:** Every table in PostgreSQL has RLS explicitly enabled (`ALTER TABLE ... ENABLE ROW LEVEL SECURITY;`).
- **Atomic RPC Functions:** All complex financial operations execute inside single PostgreSQL transaction functions (`create_expense_with_shares`, `confirm_payment_record`) with `SECURITY DEFINER` and caller membership checks.
- **Client Input Validation:**
  - Share sums must match total expense paise down to the exact single paisa (`v_total_shares = p_amount_paise`).
  - Negative amounts and negative shares are rejected via database check constraints (`CHECK (amount_paise > 0)` and `CHECK (share_paise >= 0)`).
  - Only active group members can participate in expenses.

## 4. Code & Build Protections
- **ProGuard / R8 Enabled:** Enabled for release builds in `app/build.gradle.kts` with code minification and resource shrinking.
- **Secrets Management:** Secrets and database connection strings are never committed to version control. Verified with CI secret audits.
- **Log Sanitization:** Sensitive monetary, personal, and session data is not logged in production builds.
