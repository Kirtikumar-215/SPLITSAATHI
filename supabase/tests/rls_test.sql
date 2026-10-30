-- SplitSaathi RLS Isolation, Negative Testing, and RPC Security Suite
-- Proves that non-members cannot read private data, hijack memberships, or bypass financial state machines.

BEGIN;

-- -------------------------------------------------------------------------------------------------
-- 1. Test as Sneha (User 4) who is NOT a member of Goa Trip 2026
-- -------------------------------------------------------------------------------------------------
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claims" = '{"sub": "44444444-4444-4444-4444-444444444444", "role": "authenticated"}';

DO $$
DECLARE
    cnt INT;
BEGIN
    -- Sneha should see 0 rows from Goa Trip 2026 groups
    SELECT count(*) INTO cnt FROM public.groups WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    IF cnt <> 0 THEN
        RAISE EXCEPTION 'RLS FAIL: Non-member Sneha could read private group!';
    END IF;

    -- Sneha should see 0 rows from expenses
    SELECT count(*) INTO cnt FROM public.expenses WHERE group_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    IF cnt <> 0 THEN
        RAISE EXCEPTION 'RLS FAIL: Non-member Sneha could read private group expenses!';
    END IF;

    -- Sneha should see 0 rows from contributions
    SELECT count(*) INTO cnt FROM public.contributions c
    JOIN public.expenses e ON e.id = c.expense_id
    WHERE e.group_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    IF cnt <> 0 THEN
        RAISE EXCEPTION 'RLS FAIL: Non-member Sneha could read private group contributions!';
    END IF;

    -- Sneha should NOT be able to view Rahul's private profile (no shared group)
    SELECT count(*) INTO cnt FROM public.profiles WHERE id = '11111111-1111-1111-1111-111111111111';
    IF cnt <> 0 THEN
        RAISE EXCEPTION 'PRIVACY FAIL: Non-shared user Sneha could read Rahul profile!';
    END IF;
END $$;

-- -------------------------------------------------------------------------------------------------
-- 2. Negative Test: Direct Group Membership Injection Rejected
-- -------------------------------------------------------------------------------------------------
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

-- -------------------------------------------------------------------------------------------------
-- 3. Test as Rahul (User 1) who IS the owner & active member
-- -------------------------------------------------------------------------------------------------
SET LOCAL "request.jwt.claims" = '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}';

DO $$
DECLARE
    cnt INT;
BEGIN
    SELECT count(*) INTO cnt FROM public.groups WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    IF cnt = 0 THEN
        RAISE EXCEPTION 'RLS FAIL: Active member Rahul could NOT read group!';
    END IF;

    SELECT count(*) INTO cnt FROM public.expenses WHERE group_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    IF cnt <> 2 THEN
        RAISE EXCEPTION 'RLS FAIL: Active member Rahul expected 2 expenses, got %', cnt;
    END IF;

    -- Rahul CAN view Priya's profile because they are active co-members in Goa Trip
    SELECT count(*) INTO cnt FROM public.profiles WHERE id = '22222222-2222-2222-2222-222222222222';
    IF cnt = 0 THEN
        RAISE EXCEPTION 'RLS FAIL: Co-member Rahul could not read co-member Priya profile!';
    END IF;
END $$;

-- -------------------------------------------------------------------------------------------------
-- 4. Negative Test: get_payee_upi obligation check for non-debtor
-- -------------------------------------------------------------------------------------------------
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

-- -------------------------------------------------------------------------------------------------
-- 5. Negative Test: Unauthorized expense creation attempt by non-member Sneha
-- -------------------------------------------------------------------------------------------------
SET LOCAL "request.jwt.claims" = '{"sub": "44444444-4444-4444-4444-444444444444", "role": "authenticated"}';
DO $$
DECLARE
    v_caught BOOLEAN := FALSE;
    v_exp_id UUID;
BEGIN
    BEGIN
        SELECT public.create_expense_with_shares(
            'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::UUID,
            'Sneha Unauthorized Expense',
            10000L,
            'Food',
            '44444444-4444-4444-4444-444444444444'::UUID,
            '[{"user_id": "44444444-4444-4444-4444-444444444444", "share_paise": 10000}]'::JSONB
        ) INTO v_exp_id;
    EXCEPTION WHEN OTHERS THEN
        v_caught := TRUE;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'SECURITY FAIL: Non-member was able to create group expense!';
    END IF;
END $$;

-- -------------------------------------------------------------------------------------------------
-- 6. Negative Test: Share sum mismatch rejected
-- -------------------------------------------------------------------------------------------------
SET LOCAL "request.jwt.claims" = '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}';
DO $$
DECLARE
    v_caught BOOLEAN := FALSE;
    v_exp_id UUID;
BEGIN
    BEGIN
        SELECT public.create_expense_with_shares(
            'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::UUID,
            'Math Error Expense',
            50000L, -- ₹500
            'Food',
            '11111111-1111-1111-1111-111111111111'::UUID,
            -- Sums to only ₹400 (40000 paise) instead of ₹500
            '[{"user_id": "11111111-1111-1111-1111-111111111111", "share_paise": 20000}, {"user_id": "22222222-2222-2222-2222-222222222222", "share_paise": 20000}]'::JSONB
        ) INTO v_exp_id;
    EXCEPTION WHEN OTHERS THEN
        v_caught := TRUE;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'FINANCIAL INTEGRITY FAIL: Sum of shares mismatch was not rejected!';
    END IF;
END $$;

-- -------------------------------------------------------------------------------------------------
-- 7. Negative Test: Non-member participant rejected
-- -------------------------------------------------------------------------------------------------
DO $$
DECLARE
    v_caught BOOLEAN := FALSE;
    v_exp_id UUID;
BEGIN
    BEGIN
        SELECT public.create_expense_with_shares(
            'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::UUID,
            'Invalid Participant Expense',
            20000L,
            'Food',
            '11111111-1111-1111-1111-111111111111'::UUID,
            -- User 4 (Sneha) is not a member of this group
            '[{"user_id": "11111111-1111-1111-1111-111111111111", "share_paise": 10000}, {"user_id": "44444444-4444-4444-4444-444444444444", "share_paise": 10000}]'::JSONB
        ) INTO v_exp_id;
    EXCEPTION WHEN OTHERS THEN
        v_caught := TRUE;
    END;
    IF NOT v_caught THEN
        RAISE EXCEPTION 'SECURITY FAIL: Non-member participant was accepted in group expense!';
    END IF;
END $$;

-- -------------------------------------------------------------------------------------------------
-- 8. Negative Test: Unauthorized payment confirmation rejected via RPC
-- -------------------------------------------------------------------------------------------------
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

-- -------------------------------------------------------------------------------------------------
-- 9. Negative Test: Direct Payment Record UPDATE Bypass Rejected
-- -------------------------------------------------------------------------------------------------
SET LOCAL "request.jwt.claims" = '{"sub": "11111111-1111-1111-1111-111111111111", "role": "authenticated"}';
DO $$
DECLARE
    v_caught BOOLEAN := FALSE;
    v_pay_id UUID;
BEGIN
    SELECT id INTO v_pay_id FROM public.payment_records WHERE to_user = '11111111-1111-1111-1111-111111111111' LIMIT 1;
    IF v_pay_id IS NOT NULL THEN
        BEGIN
            -- Attempt direct UPDATE bypassing confirm_payment_record RPC
            UPDATE public.payment_records
            SET status = 'paid_confirmed'
            WHERE id = v_pay_id;
        EXCEPTION WHEN OTHERS THEN
            v_caught := TRUE;
        END;
        -- If no exception thrown, check if 0 rows affected due to RLS
            IF (SELECT status FROM public.payment_records WHERE id = v_pay_id) = 'paid_confirmed' THEN
                RAISE EXCEPTION 'SECURITY FAIL: Direct UPDATE on payment_records bypassed RPC!';
            END IF;
        END IF;
    END IF;
END $$;

-- -------------------------------------------------------------------------------------------------
-- 10. Positive & Negative Tests: claim_payment RPC
-- -------------------------------------------------------------------------------------------------
-- Amit (User 3) owes Rahul for Goa Trip. Amit claims payment of ₹616.67 (61667 paise).
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
        -- 10a. Valid claim should succeed
        SELECT public.claim_payment(v_contrib_id, 61667L, 'UPI-TEST-12345', 'Settling dinner') INTO v_payment_id;
        IF v_payment_id IS NULL THEN
            RAISE EXCEPTION 'RPC FAIL: Valid payment claim returned NULL!';
        END IF;

        -- 10b. Duplicate claim while previous is pending confirmation MUST be rejected
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

-- -------------------------------------------------------------------------------------------------
-- 11. Positive Test: anonymize_user_account RPC
-- -------------------------------------------------------------------------------------------------
-- User 2 (Priya) requests account deletion / anonymization
SET LOCAL "request.jwt.claims" = '{"sub": "22222222-2222-2222-2222-222222222222", "role": "authenticated"}';
DO $$
DECLARE
    v_success BOOLEAN;
    v_name TEXT;
    v_phone TEXT;
    v_upi TEXT;
    v_consent BOOLEAN;
BEGIN
    SELECT public.anonymize_user_account() INTO v_success;
    IF NOT v_success THEN
        RAISE EXCEPTION 'RPC FAIL: anonymize_user_account returned FALSE';
    END IF;

    -- Verify that personal fields are sanitized
    SELECT display_name, phone, upi_id, upi_consent INTO v_name, v_phone, v_upi, v_consent
    FROM public.profiles
    WHERE id = '22222222-2222-2222-2222-222222222222';

    IF v_name <> 'Deactivated Member' OR v_phone IS NOT NULL OR v_upi IS NOT NULL OR v_consent <> FALSE THEN
        RAISE EXCEPTION 'ANONYMIZATION FAIL: Personal details were not properly sanitized!';
    END IF;
END $$;

ROLLBACK;

