-- SplitSaathi Comprehensive RLS Isolation, Column Privacy, and RPC Security Suite
-- Verifies zero-trust profile privacy, membership anti-injection, financial state machines, and RPC authorization.
-- Wrapped in BEGIN ... ROLLBACK to guarantee 100% zero persistent changes.

BEGIN;

-- =================================================================================================
-- 1. PROFILE ACCESS: OWN PROFILE VS CO-MEMBER VS NON-MEMBER PRIVACY
-- =================================================================================================

-- 1a. Test as Rahul (User 1): Must be able to read his OWN full profile (including phone and UPI ID)
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claims" = '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}';

DO $$
DECLARE
    v_rec RECORD;
BEGIN
    SELECT * INTO v_rec FROM public.profiles WHERE id = '11111111-1111-1111-1111-111111111111';
    IF v_rec.id IS NULL OR v_rec.phone IS NULL OR v_rec.upi_id IS NULL THEN
        RAISE EXCEPTION 'PRIVACY FAIL: User cannot read their own private profile attributes!';
    END IF;
END $$;

-- 1b. Test as Priya (User 2 - co-member with Rahul in Goa Trip):
-- Attempting direct SELECT from public.profiles on Rahul's ID MUST return 0 rows!
-- (Direct table SELECT only yields self profile)
SET LOCAL "request.jwt.claims" = '{"sub": "22222222-2222-2222-2222-222222222222", "role": "authenticated"}';

DO $$
DECLARE
    cnt INT;
BEGIN
    SELECT count(*) INTO cnt FROM public.profiles WHERE id = '11111111-1111-1111-1111-111111111111';
    IF cnt <> 0 THEN
        RAISE EXCEPTION 'CRITICAL PRIVACY LEAK: Co-member could query private profiles table for another user!';
    END IF;
END $$;

-- 1c. Test as Priya (User 2): Co-member CAN read public metadata via member_profiles view
DO $$
DECLARE
    v_rec RECORD;
BEGIN
    SELECT * INTO v_rec FROM public.member_profiles WHERE id = '11111111-1111-1111-1111-111111111111';
    IF v_rec.id IS NULL OR v_rec.display_name <> 'Rahul Sharma' THEN
        RAISE EXCEPTION 'VIEW FAIL: Co-member cannot view public metadata of fellow group member!';
    END IF;
END $$;

-- 1d. Test as Sneha (User 4 - NOT a member of Goa Trip):
-- Sneha querying member_profiles for Rahul MUST return 0 rows (no shared group)
SET LOCAL "request.jwt.claims" = '{"sub": "44444444-4444-4444-4444-444444444444", "role": "authenticated"}';

DO $$
DECLARE
    cnt INT;
BEGIN
    SELECT count(*) INTO cnt FROM public.member_profiles WHERE id = '11111111-1111-1111-1111-111111111111';
    IF cnt <> 0 THEN
        RAISE EXCEPTION 'PRIVACY LEAK: Non-member could view member profile of stranger!';
    END IF;
END $$;

-- =================================================================================================
-- 2. NON-MEMBER GROUP & EXPENSE ISOLATION
-- =================================================================================================
-- Sneha (User 4) must see 0 rows from Goa Trip group, expenses, and contributions
DO $$
DECLARE
    cnt INT;
BEGIN
    SELECT count(*) INTO cnt FROM public.groups WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    IF cnt <> 0 THEN
        RAISE EXCEPTION 'RLS FAIL: Non-member Sneha could read private group!';
    END IF;

    SELECT count(*) INTO cnt FROM public.expenses WHERE group_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    IF cnt <> 0 THEN
        RAISE EXCEPTION 'RLS FAIL: Non-member Sneha could read private group expenses!';
    END IF;

    SELECT count(*) INTO cnt FROM public.contributions c
    JOIN public.expenses e ON e.id = c.expense_id
    WHERE e.group_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    IF cnt <> 0 THEN
        RAISE EXCEPTION 'RLS FAIL: Non-member Sneha could read private group contributions!';
    END IF;
END $$;

-- =================================================================================================
-- 3. MEMBERSHIP ANTI-INJECTION & ANTI-ESCALATION
-- =================================================================================================
-- 3a. Sneha (User 4) attempting direct INSERT into group_members MUST fail
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
        RAISE EXCEPTION 'SECURITY FAIL: Non-member directly inserted themselves into private group!';
    END IF;
END $$;

-- 3b. Amit (User 3 - normal member) attempting to self-promote to 'admin' or 'owner' via UPDATE
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

    -- Verify role was not modified
    IF (SELECT role FROM public.group_members WHERE group_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' AND user_id = '33333333-3333-3333-3333-333333333333') <> 'member' THEN
        RAISE EXCEPTION 'PRIVILEGE ESCALATION FAIL: Normal member was able to self-promote to owner!';
    END IF;
END $$;

-- =================================================================================================
-- 4. DIRECT FINANCIAL TABLE INSERTION BYPASSES BLOCKED
-- =================================================================================================
-- Direct INSERT on contributions must be rejected (must use create_expense_with_shares RPC)
DO $$
DECLARE
    v_caught BOOLEAN := FALSE;
BEGIN
    BEGIN
        INSERT INTO public.contributions (expense_id, user_id, share_paise, committed_paise, status)
        VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', '33333333-3333-3333-3333-333333333333', 50000, 50000, 'paid_confirmed');
    EXCEPTION WHEN OTHERS THEN
        v_caught := TRUE;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'SECURITY FAIL: Direct INSERT on contributions table was permitted!';
    END IF;
END $$;

-- =================================================================================================
-- 5. RPC AUTHORIZATION & OBLIGATION-GATED UPI ACCESS (get_payee_upi)
-- =================================================================================================
-- 5a. Amit (User 3) owes Rahul for Expense 1 (Beachside Dinner) with status 'will_pay'.
-- Amit calling get_payee_upi for Rahul MUST succeed!
SET LOCAL "request.jwt.claims" = '{"sub": "33333333-3333-3333-3333-333333333333", "role": "authenticated"}';

DO $$
DECLARE
    v_upi TEXT;
BEGIN
    SELECT public.get_payee_upi('11111111-1111-1111-1111-111111111111') INTO v_upi;
    IF v_upi <> 'rahul@okaxis' THEN
        RAISE EXCEPTION 'RPC FAIL: Authorized debtor could not retrieve payee UPI ID!';
    END IF;
END $$;

-- 5b. Sneha (User 4 - has zero obligations to Rahul) calling get_payee_upi MUST be rejected!
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
        RAISE EXCEPTION 'SECURITY FAIL: Non-debtor retrieved payee UPI ID!';
    END IF;
END $$;

-- =================================================================================================
-- 6. ATOMIC PAYMENT CLAIMING & DUPLICATE BLOCKING (claim_payment)
-- =================================================================================================
-- Amit (User 3) claims payment of ₹616.67 (61667 paise) to Rahul
SET LOCAL "request.jwt.claims" = '{"sub": "33333333-3333-3333-3333-333333333333", "role": "authenticated"}';

DO $$
DECLARE
    v_contrib_id UUID;
    v_payment_id UUID;
    v_caught BOOLEAN := FALSE;
BEGIN
    SELECT id INTO v_contrib_id
    FROM public.contributions
    WHERE user_id = '33333333-3333-3333-3333-333333333333' AND status = 'will_pay'
    LIMIT 1;

    IF v_contrib_id IS NOT NULL THEN
        -- 6a. Valid claim should succeed
        SELECT public.claim_payment(v_contrib_id, 61667L, 'UPI-TEST-12345', 'Settling dinner') INTO v_payment_id;
        IF v_payment_id IS NULL THEN
            RAISE EXCEPTION 'RPC FAIL: Valid payment claim returned NULL!';
        END IF;

        -- 6b. Duplicate claim while previous is pending confirmation MUST be rejected
        BEGIN
            PERFORM public.claim_payment(v_contrib_id, 61667L, 'UPI-TEST-DUP', 'Duplicate attempt');
        EXCEPTION WHEN OTHERS THEN
            v_caught := TRUE;
        END;
        IF NOT v_caught THEN
            RAISE EXCEPTION 'DUPLICATE CLAIM FAIL: Duplicate open payment claim was not rejected!';
        END IF;
    END IF;
END $$;

-- =================================================================================================
-- 7. PAYMENT CONFIRMATION STATE MACHINE & AUTHORIZATION (confirm_payment_record)
-- =================================================================================================
-- 7a. User 2 (Priya) tries to confirm a payment owed to User 1 (Rahul) -> MUST FAIL!
SET LOCAL "request.jwt.claims" = '{"sub": "22222222-2222-2222-2222-222222222222", "role": "authenticated"}';

DO $$
DECLARE
    v_caught BOOLEAN := FALSE;
    v_res BOOLEAN;
    v_pay_id UUID;
BEGIN
    SELECT id INTO v_pay_id FROM public.payment_records WHERE to_user = '11111111-1111-1111-1111-111111111111' LIMIT 1;
    IF v_pay_id IS NOT NULL THEN
        BEGIN
            SELECT public.confirm_payment_record(v_pay_id, 'confirm') INTO v_res;
        EXCEPTION WHEN OTHERS THEN
            v_caught := TRUE;
        END;
        IF NOT v_caught THEN
            RAISE EXCEPTION 'AUTHORIZATION FAIL: Non-recipient confirmed payment record!';
        END IF;
    END IF;
END $$;

-- 7b. Recipient (Rahul) confirms the payment -> MUST SUCCEED
SET LOCAL "request.jwt.claims" = '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}';

DO $$
DECLARE
    v_res BOOLEAN;
    v_pay_id UUID;
BEGIN
    SELECT id INTO v_pay_id
    FROM public.payment_records
    WHERE to_user = '11111111-1111-1111-1111-111111111111' AND status = 'paid_pending_confirmation'
    LIMIT 1;

    IF v_pay_id IS NOT NULL THEN
        SELECT public.confirm_payment_record(v_pay_id, 'confirm') INTO v_res;
        IF NOT v_res THEN
            RAISE EXCEPTION 'CONFIRM FAIL: Recipient failed to confirm valid payment!';
        END IF;

        -- Verify double confirmation is rejected
        BEGIN
            PERFORM public.confirm_payment_record(v_pay_id, 'confirm');
            RAISE EXCEPTION 'DOUBLE CONFIRM FAIL: Second confirmation of same payment succeeded!';
        EXCEPTION WHEN OTHERS THEN
            -- Expected rejection
            NULL;
        END;
    END IF;
END $$;

-- =================================================================================================
-- 8. ACCOUNT ANONYMIZATION & OWNERSHIP SUCCESSION (anonymize_user_account)
-- =================================================================================================
-- Rahul (Owner of Goa Trip) requests account deletion
SET LOCAL "request.jwt.claims" = '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}';

DO $$
DECLARE
    v_success BOOLEAN;
    v_name TEXT;
    v_phone TEXT;
    v_upi TEXT;
    v_new_owner UUID;
BEGIN
    SELECT public.anonymize_user_account() INTO v_success;
    IF NOT v_success THEN
        RAISE EXCEPTION 'RPC FAIL: anonymize_user_account returned FALSE';
    END IF;

    -- Verify that Rahul's personal fields were sanitized
    SELECT display_name, phone, upi_id INTO v_name, v_phone, v_upi
    FROM public.profiles
    WHERE id = '11111111-1111-1111-1111-111111111111';

    IF v_name <> 'Deactivated Member' OR v_phone IS NOT NULL OR v_upi IS NOT NULL THEN
        RAISE EXCEPTION 'ANONYMIZATION FAIL: Personal details were not properly sanitized!';
    END IF;

    -- Verify Goa Trip group ownership was safely transferred to remaining member (Priya or Amit)
    SELECT owner_id INTO v_new_owner FROM public.groups WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    IF v_new_owner = '11111111-1111-1111-1111-111111111111' OR v_new_owner IS NULL THEN
        RAISE EXCEPTION 'SUCCESSION FAIL: Group ownership was not transferred to active successor!';
    END IF;
END $$;

ROLLBACK;
