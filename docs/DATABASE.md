# SplitSaathi Database Architecture & Security Model

## Overview
All financial amounts in SplitSaathi are stored strictly as **integer paise (`BIGINT`)** in both PostgreSQL and local Room storage. Floating point arithmetic is forbidden.

## ERD & Tables
1. **`profiles`**
   - `id`: UUID (Primary Key, matches Auth UID)
   - `display_name`: TEXT (NOT NULL)
   - `email`: TEXT (NOT NULL)
   - `avatar_key`: TEXT (e.g. `avatar_1`)
   - `upi_id`: TEXT (CHECK: `^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$`)
   - `upi_consent`: BOOLEAN (NOT NULL, default false)
   - `upi_consent_at`: TIMESTAMPTZ

2. **`groups`**
   - `id`: UUID (Primary Key)
   - `name`: TEXT (NOT NULL)
   - `description`: TEXT
   - `owner_id`: UUID (Foreign Key -> profiles.id)
   - `status`: TEXT ('active', 'archived')

3. **`group_members`**
   - `group_id`: UUID (Foreign Key -> groups.id)
   - `user_id`: UUID (Foreign Key -> profiles.id)
   - `role`: TEXT ('owner', 'admin', 'member')
   - `status`: TEXT ('active', 'removed', 'left')

4. **`group_invites`**
   - `id`: UUID (Primary Key)
   - `group_id`: UUID (Foreign Key -> groups.id)
   - `code`: TEXT (6 characters uppercase, UNIQUE)
   - `created_by`: UUID (Foreign Key -> profiles.id)
   - `uses`: INT
   - `max_uses`: INT

5. **`expenses`**
   - `id`: UUID (Primary Key)
   - `group_id`: UUID (Foreign Key -> groups.id)
   - `title`: TEXT
   - `amount_paise`: BIGINT (> 0)
   - `category`: TEXT
   - `paid_by`: UUID
   - `split_type`: TEXT ('equal', 'custom')
   - `status`: TEXT ('active', 'cancelled')

6. **`expense_shares`**
   - `expense_id`: UUID
   - `user_id`: UUID
   - `share_paise`: BIGINT (>= 0, sum == expense total)

7. **`contributions`**
   - `id`: UUID (Primary Key)
   - `expense_id`: UUID
   - `user_id`: UUID
   - `share_paise`: BIGINT
   - `committed_paise`: BIGINT (<= share_paise)
   - `status`: TEXT ('will_pay', 'paid_pending_confirmation', 'paid_confirmed', 'disputed', 'rejected_resolved', 'cancelled')

8. **`payment_records`**
   - `id`: UUID (Primary Key)
   - `contribution_id`: UUID
   - `from_user`: UUID
   - `to_user`: UUID
   - `amount_paise`: BIGINT (> 0, <= remaining owed)
   - `status`: TEXT
   - `idempotency_key`: UUID (UNIQUE)
   - `claimed_at`, `responded_at`, `dispute_reason`, `last_nudged_at`

9. **`activity_logs`**
   - Append-only audit trail logging every creation, role change, claim, and confirmation.

10. **`notifications`**
   - In-app notification center.

## Row Level Security (RLS) & RPCs
- **Group Isolation**: Only active members of a group can query its expenses, contributions, and activity logs.
- **UPI Privacy (Decision 12)**: `get_payee_upi(payee_id)` returns a member's UPI ID only if the caller has an active unpaid obligation to that member in a shared group, or is the member themselves.
- **Overpayment Prevention**: RPC enforces `amount_paise <= remaining_owed`.
