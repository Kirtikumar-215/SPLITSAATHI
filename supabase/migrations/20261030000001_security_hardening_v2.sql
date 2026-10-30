-- SplitSaathi Security Hardening Migration V2: 20261030000001_security_hardening_v2.sql
-- Purpose: Complete column-level privacy enforcement, eliminate RLS recursion, lock down financial bypasses, and secure RPC execution.
-- Fully idempotent: Safe to apply on top of 20261027000001_initial_schema.sql or 20261029000001_security_hardening.sql.

BEGIN;

-- =================================================================================================
-- 1. SECURITY DEFINER HELPER FUNCTIONS (Eliminates RLS recursion on group_members)
-- =================================================================================================

-- Helper to check if a user is an active owner or admin of a group without triggering RLS recursion
CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID, p_user_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.group_members
        WHERE group_id = p_group_id
          AND user_id = p_user_id
          AND status = 'active'
          AND role IN ('owner', 'admin')
    );
$$;

-- Helper to check if two users share at least one mutual active group
CREATE OR REPLACE FUNCTION public.shares_active_group(p_user_a UUID, p_user_b UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.group_members gm_a
        JOIN public.group_members gm_b ON gm_a.group_id = gm_b.group_id
        WHERE gm_a.user_id = p_user_a
          AND gm_a.status = 'active'
          AND gm_b.user_id = p_user_b
          AND gm_b.status = 'active'
    );
$$;

-- =================================================================================================
-- 2. PROFILE PRIVACY: ENFORCE TRUE COLUMN & ROW ISOLATION
-- =================================================================================================
-- Drop all prior wide or co-member SELECT policies on public.profiles
DROP POLICY IF EXISTS "Profiles viewable by authenticated users" ON public.profiles;
DROP POLICY IF EXISTS "Users and co-members can view profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can only view own profile" ON public.profiles;

-- Strict Zero-Trust Row Policy: Authenticated users can ONLY SELECT their OWN profile row.
-- This physically blocks any co-member from running `SELECT phone, email, upi_id FROM profiles WHERE id = ...`.
CREATE POLICY "Users can only view own profile"
ON public.profiles FOR SELECT
TO authenticated
USING (auth.uid() = id);

-- Create a Secure View for Co-Members: Exposes ONLY non-sensitive public metadata
-- (id, display_name, avatar_key, created_at). Phone, email, and UPI ID are structurally absent.
CREATE OR REPLACE VIEW public.member_profiles
WITH (security_invoker = false, security_barrier = true) AS
SELECT
    p.id,
    p.display_name,
    p.avatar_key,
    p.created_at
FROM public.profiles p
WHERE p.id = (SELECT auth.uid()) OR public.shares_active_group((SELECT auth.uid()), p.id);

GRANT SELECT ON public.member_profiles TO authenticated;

-- =================================================================================================
-- 3. GROUP MEMBERS: PREVENT DIRECT MEMBERSHIP INJECTION & RECURSION
-- =================================================================================================
DROP POLICY IF EXISTS "Authenticated can join group" ON public.group_members;
DROP POLICY IF EXISTS "Group owners and admins can add members" ON public.group_members;
DROP POLICY IF EXISTS "Active members can update member status" ON public.group_members;
DROP POLICY IF EXISTS "Admins or self can update member status" ON public.group_members;

-- Direct INSERT on group_members is strictly restricted to active group owners and admins.
-- Regular joining is strictly mediated via join_group_by_invite(code) RPC or create_group RPC.
CREATE POLICY "Group owners and admins can add members"
ON public.group_members FOR INSERT
TO authenticated
WITH CHECK (
    public.is_group_admin(group_id, auth.uid())
);

-- Updating member roles and status uses the is_group_admin helper to avoid RLS recursion.
CREATE POLICY "Admins or self can update member status"
ON public.group_members FOR UPDATE
TO authenticated
USING (
    user_id = auth.uid()
    OR
    public.is_group_admin(group_id, auth.uid())
)
WITH CHECK (
    -- Non-admins cannot promote themselves to admin or owner
    (
        user_id = auth.uid() AND
        group_members.role = 'member'
    )
    OR
    public.is_group_admin(group_id, auth.uid())
);

-- =================================================================================================
-- 4. GROUP INVITES: RESTRICT VISIBILITY AND CREATION
-- =================================================================================================
DROP POLICY IF EXISTS "Active members can view group invites" ON public.group_invites;
CREATE POLICY "Active members can view group invites"
ON public.group_invites FOR SELECT
TO authenticated
USING (public.is_group_member(group_id, auth.uid()));

DROP POLICY IF EXISTS "Admins and owners can create group invites" ON public.group_invites;
CREATE POLICY "Admins and owners can create group invites"
ON public.group_invites FOR INSERT
TO authenticated
WITH CHECK (
    created_by = auth.uid() AND
    public.is_group_admin(group_id, auth.uid())
);

-- =================================================================================================
-- 5. EXPENSES & CONTRIBUTIONS: LOCK DOWN DIRECT TABLE MUTATION BYPASSES
-- =================================================================================================
-- Direct INSERT on expenses must require that the caller is an active group member and created_by = auth.uid()
DROP POLICY IF EXISTS "Members can insert group expenses" ON public.expenses;
CREATE POLICY "Members can insert group expenses"
ON public.expenses FOR INSERT
TO authenticated
WITH CHECK (
    created_by = auth.uid() AND
    public.is_group_member(group_id, auth.uid()) AND
    public.is_group_member(group_id, paid_by) AND
    amount_paise > 0
);

-- Prevent direct unvalidated INSERT on expense_participants and contributions.
-- These rows MUST be created atomically via create_expense_with_shares RPC to guarantee share-sum equality!
DROP POLICY IF EXISTS "Expense creators can add participants" ON public.expense_participants;
DROP POLICY IF EXISTS "Members can create contributions" ON public.contributions;

-- Soft cancellation must only be executed by the expense creator or group admin
DROP POLICY IF EXISTS "Creators or admins can cancel expense" ON public.expenses;
CREATE POLICY "Creators or admins can cancel expense"
ON public.expenses FOR UPDATE
TO authenticated
USING (
    created_by = auth.uid() OR
    public.is_group_admin(group_id, auth.uid())
)
WITH CHECK (
    created_by = auth.uid() OR
    public.is_group_admin(group_id, auth.uid())
);

-- =================================================================================================
-- 6. PAYMENT RECORDS: ENFORCE VALID CONTRIBUTION CLAIMS AND PREVENT STATUS BYPASS
-- =================================================================================================
-- Only allow inserting payment claims where from_user is the caller, and the contribution belongs to caller
DROP POLICY IF EXISTS "Claimant can insert payment record" ON public.payment_records;
CREATE POLICY "Claimant can insert payment record"
ON public.payment_records FOR INSERT
TO authenticated
WITH CHECK (
    from_user = auth.uid() AND
    amount_paise > 0 AND
    EXISTS (
        SELECT 1 FROM public.contributions c
        JOIN public.expenses e ON e.id = c.expense_id
        WHERE c.id = contribution_id
          AND c.user_id = auth.uid()
          AND e.paid_by = to_user
          AND e.status = 'active'
          AND public.is_group_member(e.group_id, auth.uid())
    )
);

-- Disallow direct raw table UPDATE on payment_records by regular client calls.
-- Status transitions (confirm/dispute) MUST be executed through confirm_payment_record RPC.
DROP POLICY IF EXISTS "Receiver can update payment record" ON public.payment_records;

-- =================================================================================================
-- 7. RPC: SAFE ATOMIC PAYMENT CLAIM (claim_payment)
-- =================================================================================================
CREATE OR REPLACE FUNCTION public.claim_payment(
    p_contribution_id UUID,
    p_amount_paise BIGINT,
    p_upi_reference TEXT DEFAULT NULL,
    p_note TEXT DEFAULT NULL,
    p_idempotency_key UUID DEFAULT gen_random_uuid()
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller_id UUID := auth.uid();
    v_contribution RECORD;
    v_expense RECORD;
    v_payment_id UUID;
    v_already_confirmed BIGINT := 0;
    v_remaining_owed BIGINT := 0;
BEGIN
    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF p_amount_paise <= 0 THEN
        RAISE EXCEPTION 'Payment amount must be greater than zero';
    END IF;

    -- Concurrency defense: lock contribution row to prevent simultaneous race conditions
    SELECT * INTO v_contribution
    FROM public.contributions
    WHERE id = p_contribution_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Contribution not found';
    END IF;

    IF v_contribution.user_id != v_caller_id THEN
        RAISE EXCEPTION 'Cannot claim payment for another member';
    END IF;

    -- Fetch associated expense
    SELECT * INTO v_expense
    FROM public.expenses
    WHERE id = v_contribution.expense_id;

    IF v_expense.status != 'active' THEN
        RAISE EXCEPTION 'Cannot pay for a cancelled expense';
    END IF;

    -- Sanity check: payment claim cannot exceed total expense amount
    IF p_amount_paise > v_expense.amount_paise THEN
        RAISE EXCEPTION 'Payment claim exceeds total expense amount';
    END IF;

    -- Calculate remaining obligation
    SELECT COALESCE(SUM(amount_paise), 0)::BIGINT INTO v_already_confirmed
    FROM public.payment_records
    WHERE contribution_id = p_contribution_id AND status = 'paid_confirmed';

    v_remaining_owed := v_contribution.share_paise - v_already_confirmed;

    -- Concurrency check: block duplicate open claims for the same obligation
    IF EXISTS (
        SELECT 1 FROM public.payment_records
        WHERE contribution_id = p_contribution_id AND status = 'paid_pending_confirmation'
    ) THEN
        RAISE EXCEPTION 'A payment claim is already pending confirmation for this obligation';
    END IF;

    -- Insert payment record
    INSERT INTO public.payment_records (
        contribution_id, from_user, to_user, amount_paise, upi_reference, note,
        status, idempotency_key, claimed_at
    ) VALUES (
        p_contribution_id, v_caller_id, v_expense.paid_by, p_amount_paise, p_upi_reference, p_note,
        'paid_pending_confirmation', p_idempotency_key, NOW()
    ) RETURNING id INTO v_payment_id;

    -- Update contribution status to pending confirmation
    UPDATE public.contributions
    SET status = 'paid_pending_confirmation',
        updated_at = NOW()
    WHERE id = p_contribution_id;

    -- Activity audit log
    INSERT INTO public.activity_logs (group_id, actor_id, entity_type, entity_id, action, new_value)
    VALUES (
        v_expense.group_id, v_caller_id, 'payment_record', v_payment_id, 'claimed',
        jsonb_build_object('amount_paise', p_amount_paise, 'to_user', v_expense.paid_by)
    );

    -- Notify recipient
    INSERT INTO public.notifications (user_id, group_id, type, title, body, payload)
    VALUES (
        v_expense.paid_by,
        v_expense.group_id,
        'payment_claimed',
        'Payment Claimed',
        format('A payment claim of ₹%s was submitted for "%s". Please confirm.', (p_amount_paise::numeric / 100)::text, v_expense.title),
        jsonb_build_object('payment_id', v_payment_id, 'amount_paise', p_amount_paise)
    );

    RETURN v_payment_id;
END;
$$;

-- =================================================================================================
-- 8. RPC: SAFE ACCOUNT DELETION & ANONYMIZATION (anonymize_user_account)
-- =================================================================================================
CREATE OR REPLACE FUNCTION public.anonymize_user_account()
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_owned_group RECORD;
    v_successor UUID;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- If user owns active groups, transfer ownership to the oldest active admin or member
    FOR v_owned_group IN
        SELECT id FROM public.groups WHERE owner_id = v_user_id AND status = 'active'
    LOOP
        SELECT user_id INTO v_successor
        FROM public.group_members
        WHERE group_id = v_owned_group.id
          AND user_id != v_user_id
          AND status = 'active'
        ORDER BY CASE WHEN role = 'admin' THEN 0 ELSE 1 END, joined_at ASC
        LIMIT 1;

        IF v_successor IS NOT NULL THEN
            UPDATE public.groups SET owner_id = v_successor WHERE id = v_owned_group.id;
            UPDATE public.group_members SET role = 'owner' WHERE group_id = v_owned_group.id AND user_id = v_successor;
        ELSE
            -- No other active members in this group, archive it safely
            UPDATE public.groups SET status = 'archived' WHERE id = v_owned_group.id;
        END IF;
    END LOOP;

    -- Anonymize personal profile attributes
    UPDATE public.profiles
    SET display_name = 'Deactivated Member',
        phone = NULL,
        email = NULL,
        upi_id = NULL,
        upi_consent = FALSE,
        upi_consent_at = NULL,
        avatar_key = 'avatar_1',
        updated_at = NOW()
    WHERE id = v_user_id;

    -- Mark memberships as left
    UPDATE public.group_members
    SET status = 'left'
    WHERE user_id = v_user_id;

    -- Soft-expire active invites created by this user
    UPDATE public.group_invites
    SET status = 'revoked'
    WHERE created_by = v_user_id AND status = 'active';

    -- Financial ledger history (expenses, contributions, payment_records) remains intact
    -- to preserve group balance integrity for other members.

    RETURN TRUE;
END;
$$;

-- =================================================================================================
-- 9. RPC FUNCTION PRIVILEGE REVOCATIONS & SEARCH PATH HARDENING
-- =================================================================================================
-- Harden search_path on all stored functions
ALTER FUNCTION public.is_group_member(UUID, UUID) SET search_path = public, pg_temp;
ALTER FUNCTION public.create_group(TEXT, TEXT) SET search_path = public, pg_temp;
ALTER FUNCTION public.create_expense_with_shares(UUID, TEXT, BIGINT, TEXT, UUID, JSONB, TEXT, TEXT) SET search_path = public, pg_temp;
ALTER FUNCTION public.confirm_payment_record(UUID, TEXT, TEXT) SET search_path = public, pg_temp;
ALTER FUNCTION public.compute_group_balances(UUID) SET search_path = public, pg_temp;
ALTER FUNCTION public.join_group_by_invite(TEXT) SET search_path = public, pg_temp;
ALTER FUNCTION public.join_group_by_code(TEXT) SET search_path = public, pg_temp;
ALTER FUNCTION public.get_payee_upi(UUID) SET search_path = public, pg_temp;
ALTER FUNCTION public.is_group_admin(UUID, UUID) SET search_path = public, pg_temp;
ALTER FUNCTION public.shares_active_group(UUID, UUID) SET search_path = public, pg_temp;
ALTER FUNCTION public.claim_payment(UUID, BIGINT, TEXT, TEXT, UUID) SET search_path = public, pg_temp;
ALTER FUNCTION public.anonymize_user_account() SET search_path = public, pg_temp;

-- Revoke execute from unauthenticated (PUBLIC / anon) and grant exclusively to authenticated
REVOKE ALL ON FUNCTION public.is_group_member(UUID, UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.create_group(TEXT, TEXT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.create_expense_with_shares(UUID, TEXT, BIGINT, TEXT, UUID, JSONB, TEXT, TEXT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.confirm_payment_record(UUID, TEXT, TEXT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.compute_group_balances(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.join_group_by_invite(TEXT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.join_group_by_code(TEXT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_payee_upi(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_group_admin(UUID, UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.shares_active_group(UUID, UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.claim_payment(UUID, BIGINT, TEXT, TEXT, UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.anonymize_user_account() FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.is_group_member(UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_group(TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_expense_with_shares(UUID, TEXT, BIGINT, TEXT, UUID, JSONB, TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.confirm_payment_record(UUID, TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.compute_group_balances(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.join_group_by_invite(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.join_group_by_code(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_payee_upi(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_group_admin(UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.shares_active_group(UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.claim_payment(UUID, BIGINT, TEXT, TEXT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.anonymize_user_account() TO authenticated;

COMMIT;
