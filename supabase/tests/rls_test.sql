-- SplitSaathi RLS Isolation, Negative Testing, and RPC Security Suite
-- Proves that non-members cannot read private data or execute unauthorized financial transactions

BEGIN;

-- 1. Test as Sneha (User 4) who is NOT a member of Goa Trip 2026
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
END $$;

-- 2. Test as Rahul (User 1) who IS the owner & active member
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
END $$;

-- 3. Negative Test: get_payee_upi obligation check for non-debtor
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

-- 4. Negative Test: Unauthorized expense creation attempt by non-member Sneha
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

-- 5. Negative Test: Share sum mismatch rejected
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

-- 6. Negative Test: Non-member participant rejected
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

-- 7. Negative Test: Unauthorized payment confirmation rejected
-- User 2 (Priya) tries to confirm a payment owed to User 1 (Rahul)
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

ROLLBACK;
