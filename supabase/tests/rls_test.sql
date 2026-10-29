-- SplitSaathi RLS Isolation and Role Security Test
-- Proves that non-members cannot read group records or access unauthorized data

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

-- 3. Test get_payee_upi obligation check
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
        RAISE EXCEPTION 'RLS FAIL: Non-debtor retrieved payee UPI ID!';
    END IF;
END $$;

ROLLBACK;
