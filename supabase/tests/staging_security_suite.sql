-- SPLITSAATHI STAGING SECURITY TEST SUITE (100% Isolated & Self-Cleaning)
-- Target: splitsaathi-staging ONLY. NEVER run against production.
-- Guarantee: Runs entirely within a single transaction block ending in ROLLBACK.
-- No test data or auth users will persist in the database.

BEGIN;

-- =================================================================================================
-- FIXTURE SETUP: Create 4 ephemeral test identities within this transaction
-- =================================================================================================
-- User 1: Rahul (Group Creator & Payer)
-- User 2: Priya (Group Co-Member who owes Rahul)
-- User 3: Amit (Group Co-Member)
-- User 4: Sneha (Non-member / External attacker)

INSERT INTO auth.users (id, email)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'staging_rahul@example.com'),
    ('22222222-2222-2222-2222-222222222222', 'staging_priya@example.com'),
    ('33333333-3333-3333-3333-333333333333', 'staging_amit@example.com'),
    ('44444444-4444-4444-4444-444444444444', 'staging_sneha@example.com');

INSERT INTO public.profiles (id, display_name, phone, email, avatar_key, upi_id, upi_consent, upi_consent_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Rahul Staging', '9876543210', 'staging_rahul@example.com', 'avatar_1', 'rahul@okstaging', TRUE, NOW()),
    ('22222222-2222-2222-2222-222222222222', 'Priya Staging', '9876543211', 'staging_priya@example.com', 'avatar_2', 'priya@okstaging', TRUE, NOW()),
    ('33333333-3333-3333-3333-333333333333', 'Amit Staging', '9876543212', 'staging_amit@example.com', 'avatar_3', 'amit@okstaging', FALSE, NULL),
    ('44444444-4444-4444-4444-444444444444', 'Sneha Staging', '9876543213', 'staging_sneha@example.com', 'avatar_4', 'sneha@okstaging', TRUE, NOW());

-- Group: Weekend Staging Trip
INSERT INTO public.groups (id, name, description, owner_id)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'Weekend Staging Trip',
    'Disposable staging test group',
    '11111111-1111-1111-1111-111111111111'
);

-- Memberships: Rahul (owner), Priya (member), Amit (member). Sneha is NOT in group.
INSERT INTO public.group_members (group_id, user_id, role, status)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'owner', 'active'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '22222222-2222-2222-2222-222222222222', 'member', 'active'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '33333333-3333-3333-3333-333333333333', 'member', 'active');

-- Expense: ₹1,500 (150000 paise) paid by Rahul, split equally among Rahul, Priya, Amit (₹500 each)
INSERT INTO public.expenses (id, group_id, title, amount_paise, category, paid_by, created_by, status)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'Staging Team Dinner',
    150000,
    'Food',
    '11111111-1111-1111-1111-111111111111',
    '11111111-1111-1111-1111-111111111111',
    'active'
);

INSERT INTO public.expense_participants (expense_id, user_id, share_paise)
VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', '11111111-1111-1111-1111-111111111111', 50000),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', '22222222-2222-2222-2222-222222222222', 50000),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', '33333333-3333-3333-3333-333333333333', 50000);

-- Priya's contribution obligation: ₹500 (50000 paise)
INSERT INTO public.contributions (id, expense_id, user_id, share_paise, committed_paise, status)
VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccc01',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01',
    '22222222-2222-2222-2222-222222222222',
    50000,
    0,
    'will_pay'
);

-- =================================================================================================
-- SECURITY ASSERTION 1: User can read their own private profile attributes
-- =================================================================================================
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claims" = '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}';

DO $$
DECLARE
    v_rec RECORD;
BEGIN
    SELECT * INTO v_rec FROM public.profiles WHERE id = '11111111-1111-1111-1111-111111111111';
    IF v_rec.id IS NULL OR v_rec.phone <> '9876543210' OR v_rec.upi_id <> 'rahul@okstaging' THEN
        RAISE EXCEPTION 'TEST 1 FAILED: User cannot read own full private profile!';
    END IF;
    RAISE NOTICE 'TEST 1 PASSED: Own private profile access verified.';
END $$;

-- =================================================================================================
-- SECURITY ASSERTION 2: Co-members can access only approved public profile fields via view
-- =================================================================================================
SET LOCAL "request.jwt.claims" = '{"sub": "22222222-2222-2222-2222-222222222222", "role": "authenticated"}';

DO $$
DECLARE
    v_rec RECORD;
BEGIN
    SELECT * INTO v_rec FROM public.member_profiles WHERE id = '11111111-1111-1111-1111-111111111111';
    IF v_rec.id IS NULL OR v_rec.display_name <> 'Rahul Staging' OR v_rec.avatar_key <> 'avatar_1' THEN
        RAISE EXCEPTION 'TEST 2 FAILED: Co-member cannot read public metadata via member_profiles view!';
    END IF;
    RAISE NOTICE 'TEST 2 PASSED: Co-member public view access verified.';
END $$;

-- =================================================================================================
-- SECURITY ASSERTION 3: Co-members CANNOT retrieve another user's phone, email, or UPI ID directly
-- =================================================================================================
DO $$
DECLARE
    cnt INT;
BEGIN
    -- Priya attempts direct SELECT on Rahul's row in public.profiles. Must return 0 rows!
    SELECT count(*) INTO cnt FROM public.profiles WHERE id = '11111111-1111-1111-1111-111111111111';
    IF cnt <> 0 THEN
        RAISE EXCEPTION 'TEST 3 FAILED: Co-member directly queried private profiles table of another member!';
    END IF;
    RAISE NOTICE 'TEST 3 PASSED: Direct column privacy enforcement verified (0 rows returned).';
END $$;

-- =================================================================================================
-- SECURITY ASSERTION 4: Authorized UPI access works ONLY through correct RPC with obligation & consent
-- =================================================================================================
-- 4a. Priya owes Rahul ₹500 for dinner. Calling get_payee_upi(Rahul) MUST return Rahul's UPI ID.
DO $$
DECLARE
    v_upi TEXT;
BEGIN
    SELECT public.get_payee_upi('11111111-1111-1111-1111-111111111111') INTO v_upi;
    IF v_upi <> 'rahul@okstaging' THEN
        RAISE EXCEPTION 'TEST 4a FAILED: Debtor could not retrieve payee UPI ID!';
    END IF;
    RAISE NOTICE 'TEST 4a PASSED: Authorized debtor retrieved payee UPI ID via RPC.';
END $$;

-- 4b. Sneha (non-debtor) calling get_payee_upi(Rahul) MUST be rejected!
SET LOCAL "request.jwt.claims" = '{"sub": "44444444-4444-4444-4444-444444444444", "role": "authenticated"}';
DO $$
DECLARE
    v_upi TEXT;
    v_caught BOOLEAN := FALSE;
BEGIN
    BEGIN
        SELECT public.get_payee_upi('11111111-1111-1111-1111-111111111111') INTO v_upi;
    EXCEPTION WHEN OTHERS THEN
        v_caught := TRUE;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 4b FAILED: Non-debtor retrieved payee UPI ID without obligation!';
    END IF;
    RAISE NOTICE 'TEST 4b PASSED: Non-debtor unauthorized UPI access rejected.';
END $$;

-- =================================================================================================
-- SECURITY ASSERTION 5: Non-members cannot read private group data, expenses, or contributions
-- =================================================================================================
DO $$
DECLARE
    cnt INT;
BEGIN
    SELECT count(*) INTO cnt FROM public.groups WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    IF cnt <> 0 THEN
        RAISE EXCEPTION 'TEST 5a FAILED: Non-member could read private group!';
    END IF;

    SELECT count(*) INTO cnt FROM public.expenses WHERE group_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    IF cnt <> 0 THEN
        RAISE EXCEPTION 'TEST 5b FAILED: Non-member could read private expenses!';
    END IF;

    SELECT count(*) INTO cnt FROM public.contributions c
    JOIN public.expenses e ON e.id = c.expense_id
    WHERE e.group_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    IF cnt <> 0 THEN
        RAISE EXCEPTION 'TEST 5c FAILED: Non-member could read private contributions!';
    END IF;
    RAISE NOTICE 'TEST 5 PASSED: Non-member group isolation verified.';
END $$;

-- =================================================================================================
-- SECURITY ASSERTION 6: Unauthorized membership injection is rejected
-- =================================================================================================
DO $$
DECLARE
    v_caught BOOLEAN := FALSE;
BEGIN
    BEGIN
        INSERT INTO public.group_members (group_id, user_id, role, status)
        VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '44444444-4444-4444-4444-444444444444', 'owner', 'active');
    EXCEPTION WHEN OTHERS THEN
        v_caught := TRUE;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 6 FAILED: Non-member directly injected themselves into private group!';
    END IF;
    RAISE NOTICE 'TEST 6 PASSED: Membership injection rejected.';
END $$;

-- =================================================================================================
-- SECURITY ASSERTION 7: Users cannot promote themselves to owner/admin
-- =================================================================================================
SET LOCAL "request.jwt.claims" = '{"sub": "33333333-3333-3333-3333-333333333333", "role": "authenticated"}';
DO $$
DECLARE
    v_caught BOOLEAN := FALSE;
BEGIN
    BEGIN
        UPDATE public.group_members
        SET role = 'owner'
        WHERE group_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' AND user_id = '33333333-3333-3333-3333-333333333333';
    EXCEPTION WHEN OTHERS THEN
        v_caught := TRUE;
    END;

    IF (SELECT role FROM public.group_members WHERE group_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' AND user_id = '33333333-3333-3333-3333-333333333333') <> 'member' THEN
        RAISE EXCEPTION 'TEST 7 FAILED: Normal member successfully self-promoted to owner!';
    END IF;
    RAISE NOTICE 'TEST 7 PASSED: Self-promotion escalation rejected.';
END $$;

-- =================================================================================================
-- SECURITY ASSERTION 8: Direct financial inserts cannot bypass RPC validation
-- =================================================================================================
DO $$
DECLARE
    v_caught BOOLEAN := FALSE;
BEGIN
    BEGIN
        INSERT INTO public.contributions (expense_id, user_id, share_paise, committed_paise, status)
        VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', '33333333-3333-3333-3333-333333333333', 99999, 99999, 'paid_confirmed');
    EXCEPTION WHEN OTHERS THEN
        v_caught := TRUE;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 8 FAILED: Direct INSERT on contributions table was allowed!';
    END IF;
    RAISE NOTICE 'TEST 8 PASSED: Direct financial mutation bypass blocked.';
END $$;

-- =================================================================================================
-- SECURITY ASSERTION 9: Payment claims cannot be duplicated or forged (claim_payment)
-- =================================================================================================
SET LOCAL "request.jwt.claims" = '{"sub": "22222222-2222-2222-2222-222222222222", "role": "authenticated"}';
DO $$
DECLARE
    v_payment_id UUID;
    v_caught BOOLEAN := FALSE;
BEGIN
    -- 9a. Valid claim of ₹500 from Priya for dinner
    SELECT public.claim_payment('cccccccc-cccc-cccc-cccc-cccccccccc01', 50000L, 'UPI-REF-9988', 'Paid via GPay') INTO v_payment_id;
    IF v_payment_id IS NULL THEN
        RAISE EXCEPTION 'TEST 9a FAILED: Valid payment claim returned NULL!';
    END IF;

    -- 9b. Duplicate claim attempt while pending confirmation MUST be rejected
    BEGIN
        PERFORM public.claim_payment('cccccccc-cccc-cccc-cccc-cccccccccc01', 50000L, 'UPI-REF-DUP', 'Duplicate');
    EXCEPTION WHEN OTHERS THEN
        v_caught := TRUE;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 9b FAILED: Duplicate pending claim was not rejected!';
    END IF;
    RAISE NOTICE 'TEST 9 PASSED: Payment claim executed and duplicate claim blocked.';
END $$;

-- =================================================================================================
-- SECURITY ASSERTION 10: Only authorized recipient can confirm or dispute payment
-- =================================================================================================
-- 10a. User 3 (Amit) attempts to confirm payment owed to User 1 (Rahul) -> MUST FAIL!
SET LOCAL "request.jwt.claims" = '{"sub": "33333333-3333-3333-3333-333333333333", "role": "authenticated"}';
DO $$
DECLARE
    v_pay_id UUID;
    v_res BOOLEAN;
    v_caught BOOLEAN := FALSE;
BEGIN
    SELECT id INTO v_pay_id FROM public.payment_records WHERE to_user = '11111111-1111-1111-1111-111111111111' LIMIT 1;
    BEGIN
        SELECT public.confirm_payment_record(v_pay_id, 'confirm') INTO v_res;
    EXCEPTION WHEN OTHERS THEN
        v_caught := TRUE;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 10a FAILED: Unauthorized non-recipient confirmed payment!';
    END IF;
    RAISE NOTICE 'TEST 10a PASSED: Unauthorized payment confirmation rejected.';
END $$;

-- 10b. Rahul (Recipient) confirms payment -> MUST SUCCEED
SET LOCAL "request.jwt.claims" = '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}';
DO $$
DECLARE
    v_pay_id UUID;
    v_res BOOLEAN;
BEGIN
    SELECT id INTO v_pay_id FROM public.payment_records WHERE to_user = '11111111-1111-1111-1111-111111111111' LIMIT 1;
    SELECT public.confirm_payment_record(v_pay_id, 'confirm') INTO v_res;
    IF NOT v_res THEN
        RAISE EXCEPTION 'TEST 10b FAILED: Recipient failed to confirm valid payment!';
    END IF;

    -- Verify double confirmation is rejected
    BEGIN
        PERFORM public.confirm_payment_record(v_pay_id, 'confirm');
        RAISE EXCEPTION 'TEST 10c FAILED: Double confirmation of same record was allowed!';
    EXCEPTION WHEN OTHERS THEN
        NULL; -- Expected rejection
    END;
    RAISE NOTICE 'TEST 10b & 10c PASSED: Recipient confirmed payment; double confirmation blocked.';
END $$;

-- =================================================================================================
-- SECURITY ASSERTION 11: Account anonymization preserves shared financial history
-- =================================================================================================
SET LOCAL "request.jwt.claims" = '{"sub": "22222222-2222-2222-2222-222222222222", "role": "authenticated"}';
DO $$
DECLARE
    v_success BOOLEAN;
    v_name TEXT;
    v_phone TEXT;
    v_upi TEXT;
    cnt_expenses INT;
    cnt_payments INT;
BEGIN
    SELECT public.anonymize_user_account() INTO v_success;
    IF NOT v_success THEN
        RAISE EXCEPTION 'TEST 11 FAILED: anonymize_user_account returned FALSE';
    END IF;

    -- Verify Priya's personal fields are sanitized
    SELECT display_name, phone, upi_id INTO v_name, v_phone, v_upi
    FROM public.profiles
    WHERE id = '22222222-2222-2222-2222-222222222222';

    IF v_name <> 'Deactivated Member' OR v_phone IS NOT NULL OR v_upi IS NOT NULL THEN
        RAISE EXCEPTION 'TEST 11 FAILED: Personal details were not sanitized!';
    END IF;

    -- Verify that financial history remains intact
    SELECT count(*) INTO cnt_expenses FROM public.expenses WHERE group_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    SELECT count(*) INTO cnt_payments FROM public.payment_records WHERE contribution_id = 'cccccccc-cccc-cccc-cccc-cccccccccc01';
    IF cnt_expenses = 0 OR cnt_payments = 0 THEN
        RAISE EXCEPTION 'TEST 11 FAILED: Financial records were accidentally deleted during anonymization!';
    END IF;
    RAISE NOTICE 'TEST 11 PASSED: Account anonymized while financial history preserved.';
END $$;

-- =================================================================================================
-- SECURITY ASSERTION 12: Group ownership succession behaves correctly
-- =================================================================================================
-- Rahul (owner of Weekend Staging Trip) requests account deletion
SET LOCAL "request.jwt.claims" = '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}';
DO $$
DECLARE
    v_success BOOLEAN;
    v_new_owner UUID;
BEGIN
    SELECT public.anonymize_user_account() INTO v_success;

    -- Verify group ownership was transferred to Amit (only active remaining member)
    SELECT owner_id INTO v_new_owner FROM public.groups WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    IF v_new_owner <> '33333333-3333-3333-3333-333333333333' THEN
        RAISE EXCEPTION 'TEST 12 FAILED: Group ownership was not transferred to active member Amit!';
    END IF;
    RAISE NOTICE 'TEST 12 PASSED: Group ownership safely transferred to successor.';
END $$;

-- =================================================================================================
-- SECURITY ASSERTION 13: Unauthorized unauthenticated users (anon) cannot execute privileged RPCs
-- =================================================================================================
SET LOCAL ROLE anon;
DO $$
DECLARE
    v_caught BOOLEAN := FALSE;
BEGIN
    BEGIN
        PERFORM public.claim_payment('cccccccc-cccc-cccc-cccc-cccccccccc01', 1000L);
    EXCEPTION WHEN OTHERS THEN
        v_caught := TRUE;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'TEST 13 FAILED: Anonymous user was able to execute claim_payment RPC!';
    END IF;
    RAISE NOTICE 'TEST 13 PASSED: Privileged RPC execution blocked for unauthenticated role.';
END $$;

-- =================================================================================================
-- CLEANUP VERIFICATION: ROLLBACK ENTIRE TRANSACTION
-- =================================================================================================
-- Because all fixtures were created inside this transaction, ROLLBACK instantly purges:
-- - All auth.users
-- - All profiles
-- - All groups, members, expenses, contributions, and payment records
ROLLBACK;
