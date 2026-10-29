# SplitSaathi — Release Readiness & Verification Checklist

---

## 1. Release Hygiene & Production Gate Status

| Domain | Criteria | Verification Status | Evidence / Notes |
|:---|:---|:---|:---|
| **Secret Sanitization** | No active passwords, production service keys, or private tokens committed in tracked files. | **VERIFIED PASS** | Secret scan across codebase returns 0 hits in tracked files. Connection strings parameterized with environment variables. |
| **Financial Precision** | Zero floating-point arithmetic on currency; strictly integer paise (`Long`). | **VERIFIED PASS** | `MoneyUtils` and database tables store paise exclusively. Remainder paise deterministically distributed. |
| **Database Security (RLS)** | All PostgreSQL tables protected by Row Level Security; multi-tenant isolation. | **VERIFIED PASS** | 100% of tables have `ENABLE ROW LEVEL SECURITY`. Negative authorization checks enforce membership. |
| **Data Protection (Backup)** | Credentials and session preferences excluded from device cloud backup. | **VERIFIED PASS** | `backup_rules.xml` and `data_extraction_rules.xml` exclude `splitsaathi_user_session.xml`. |
| **CSV Security** | Spreadsheet export protected against CSV formula injection. | **VERIFIED PASS** | Cells starting with `=`, `+`, `-`, `@`, `\t` are escaped with leading single quote. |
| **Privacy Compliance** | Privacy policy, terms, refund policy, and account deletion workflow accessible in-app. | **VERIFIED PASS** | Fully implemented in `ProfileScreen.kt` and documented in `docs/legal/`. |
| **Continuous Integration** | Automated test suite passes on JVM without emulator dependencies. | **VERIFIED PASS** | 33 JVM unit/Robolectric tests pass cleanly via Gradle. GitHub Actions workflow configured in `.github/workflows/ci.yml`. |

---

## 2. Manual Administrative Steps for App Owner
1. **Rotate Supabase Database Password:** Because a connection string with a live password was present prior to remediation in local scripts, reset the database password immediately in the Supabase Dashboard (`Project Settings -> Database`).
2. **Apply Supabase Migrations:** Run migrations against the project with `SUPABASE_DB_URL="postgresql://..." node supabase/apply_migrations.js`.
3. **Configure Release Keystore:** For Google Play publishing, set up your release keystore according to Android Developer documentation and sign the release AAB/APK.
