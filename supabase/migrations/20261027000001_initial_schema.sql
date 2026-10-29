-- SplitSaathi Database Migration: V1 Initial Schema, Constraints, RLS, and RPCs
-- Currency: INR (all money stored strictly in bigint paise)

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. PROFILES TABLE (Full Name, Phone, UPI ID, Avatar)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL CHECK (char_length(trim(display_name)) >= 1),
    phone TEXT CHECK (phone IS NULL OR phone ~* '^[6-9][0-9]{9}$'),
    email TEXT,
    avatar_key TEXT NOT NULL DEFAULT 'avatar_1',
    upi_id TEXT CHECK (upi_id IS NULL OR upi_id ~* '^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$'),
    upi_consent BOOLEAN NOT NULL DEFAULT FALSE,
    upi_consent_at TIMESTAMPTZ,
    preferences JSONB NOT NULL DEFAULT '{"notify_expense": true, "notify_payment": true, "notify_nudge": true}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. GROUPS TABLE
CREATE TABLE IF NOT EXISTS public.groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL CHECK (char_length(trim(name)) >= 1),
    description TEXT,
    owner_id UUID NOT NULL REFERENCES public.profiles(id),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. GROUP MEMBERS TABLE
CREATE TABLE IF NOT EXISTS public.group_members (
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('owner', 'admin', 'member')),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'removed', 'left')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (group_id, user_id)
);

-- 4. GROUP INVITES TABLE (6-character unique code)
CREATE TABLE IF NOT EXISTS public.group_invites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    code TEXT UNIQUE NOT NULL CHECK (char_length(code) = 6),
    created_by UUID NOT NULL REFERENCES public.profiles(id),
    expires_at TIMESTAMPTZ,
    max_uses INT DEFAULT NULL,
    uses INT NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'revoked', 'expired')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. EXPENSES TABLE (amount in integer paise)
CREATE TABLE IF NOT EXISTS public.expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    title TEXT NOT NULL CHECK (char_length(trim(title)) >= 1),
    description TEXT,
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    category TEXT NOT NULL CHECK (category IN ('Groceries', 'Rent', 'Internet/Bills', 'Food', 'Travel', 'Project Materials', 'Event', 'Utilities', 'Other')),
    paid_by UUID NOT NULL REFERENCES public.profiles(id),
    created_by UUID NOT NULL REFERENCES public.profiles(id),
    expense_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notes TEXT,
    split_type TEXT NOT NULL DEFAULT 'equal' CHECK (split_type IN ('equal', 'custom')),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'cancelled')),
    cancel_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. EXPENSE PARTICIPANTS TABLE (assigned share per participant in paise)
CREATE TABLE IF NOT EXISTS public.expense_participants (
    expense_id UUID NOT NULL REFERENCES public.expenses(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    share_paise BIGINT NOT NULL CHECK (share_paise >= 0),
    PRIMARY KEY (expense_id, user_id)
);

-- Backward compatibility view for expense_shares
CREATE OR REPLACE VIEW public.expense_shares AS
    SELECT expense_id, user_id, share_paise FROM public.expense_participants;

-- 7. CONTRIBUTIONS TABLE (tracks commitment & payment status per member)
CREATE TABLE IF NOT EXISTS public.contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id UUID NOT NULL REFERENCES public.expenses(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    share_paise BIGINT NOT NULL CHECK (share_paise >= 0),
    committed_paise BIGINT NOT NULL DEFAULT 0 CHECK (committed_paise >= 0),
    status TEXT NOT NULL DEFAULT 'will_pay' CHECK (status IN ('will_pay', 'paid_pending_confirmation', 'paid_confirmed', 'disputed', 'rejected_resolved', 'cancelled')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 8. PAYMENT RECORDS TABLE (tracking claimed & confirmed payments)
CREATE TABLE IF NOT EXISTS public.payment_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contribution_id UUID NOT NULL REFERENCES public.contributions(id) ON DELETE CASCADE,
    from_user UUID NOT NULL REFERENCES public.profiles(id),
    to_user UUID NOT NULL REFERENCES public.profiles(id),
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    upi_reference TEXT,
    note TEXT,
    status TEXT NOT NULL DEFAULT 'paid_pending_confirmation' CHECK (status IN ('paid_pending_confirmation', 'paid_confirmed', 'disputed', 'rejected_resolved', 'cancelled')),
    idempotency_key UUID UNIQUE NOT NULL,
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMPTZ,
    responded_by UUID REFERENCES public.profiles(id),
    dispute_reason TEXT,
    resolved_by UUID REFERENCES public.profiles(id)
);

-- 9. ACTIVITY LOGS TABLE (append-only audit trail)
CREATE TABLE IF NOT EXISTS public.activity_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES public.profiles(id),
    entity_type TEXT NOT NULL,
    entity_id UUID NOT NULL,
    action TEXT NOT NULL,
    old_value JSONB,
    new_value JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 10. NOTIFICATIONS TABLE (in-app notifications)
CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    type TEXT NOT NULL CHECK (type IN ('new_expense', 'contribution_assigned', 'commitment_changed', 'payment_claimed', 'payment_confirmed', 'payment_disputed', 'dispute_resolved', 'nudge', 'member_joined')),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- INDEXES
CREATE INDEX IF NOT EXISTS idx_group_members_user ON public.group_members(user_id);
CREATE INDEX IF NOT EXISTS idx_group_invites_code ON public.group_invites(code);
CREATE INDEX IF NOT EXISTS idx_expenses_group ON public.expenses(group_id);
CREATE INDEX IF NOT EXISTS idx_expense_participants_user ON public.expense_participants(user_id);
CREATE INDEX IF NOT EXISTS idx_contributions_expense ON public.contributions(expense_id);
CREATE INDEX IF NOT EXISTS idx_contributions_user ON public.contributions(user_id);
CREATE INDEX IF NOT EXISTS idx_payment_records_contribution ON public.payment_records(contribution_id);
CREATE INDEX IF NOT EXISTS idx_activity_logs_group ON public.activity_logs(group_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON public.notifications(user_id) WHERE is_read = FALSE;

-- ROW LEVEL SECURITY (RLS)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_invites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.expense_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contributions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payment_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.activity_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;

-- Helper function to check active group membership
CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID, p_user_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.group_members
        WHERE group_id = p_group_id AND user_id = p_user_id AND status = 'active'
    );
$$;

-- PROFILES POLICIES
CREATE POLICY "Profiles viewable by authenticated users"
ON public.profiles FOR SELECT
TO authenticated
USING (true);

CREATE POLICY "Users can insert own profile"
ON public.profiles FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can update own profile"
ON public.profiles FOR UPDATE
TO authenticated
USING (auth.uid() = id)
WITH CHECK (auth.uid() = id);

-- GROUPS POLICIES
CREATE POLICY "Active members can view group"
ON public.groups FOR SELECT
TO authenticated
USING (public.is_group_member(id, auth.uid()));

CREATE POLICY "Authenticated users can create groups"
ON public.groups FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = owner_id);

-- GROUP MEMBERS POLICIES
CREATE POLICY "Active members can view members of their groups"
ON public.group_members FOR SELECT
TO authenticated
USING (public.is_group_member(group_id, auth.uid()));

CREATE POLICY "Authenticated can join group"
ON public.group_members FOR INSERT
TO authenticated
WITH CHECK (auth.uid() = user_id);

-- EXPENSES POLICIES
CREATE POLICY "Members can view group expenses"
ON public.expenses FOR SELECT
TO authenticated
USING (public.is_group_member(group_id, auth.uid()));

CREATE POLICY "Members can insert group expenses"
ON public.expenses FOR INSERT
TO authenticated
WITH CHECK (public.is_group_member(group_id, auth.uid()));

-- EXPENSE PARTICIPANTS POLICIES
CREATE POLICY "Members can view participants for group expenses"
ON public.expense_participants FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.expenses e
        WHERE e.id = expense_id AND public.is_group_member(e.group_id, auth.uid())
    )
);

CREATE POLICY "Expense creators can add participants"
ON public.expense_participants FOR INSERT
TO authenticated
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.expenses e
        WHERE e.id = expense_id AND public.is_group_member(e.group_id, auth.uid())
    )
);

-- CONTRIBUTIONS POLICIES
CREATE POLICY "Members can view contributions for group expenses"
ON public.contributions FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1 FROM public.expenses e
        WHERE e.id = expense_id AND public.is_group_member(e.group_id, auth.uid())
    )
);

CREATE POLICY "Members can create contributions"
ON public.contributions FOR INSERT
TO authenticated
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.expenses e
        WHERE e.id = expense_id AND public.is_group_member(e.group_id, auth.uid())
    )
);

CREATE POLICY "Members can update their own contribution commitment"
ON public.contributions FOR UPDATE
TO authenticated
USING (
    user_id = auth.uid() OR
    EXISTS (
        SELECT 1 FROM public.expenses e
        WHERE e.id = expense_id AND e.paid_by = auth.uid()
    )
);

-- PAYMENT RECORDS POLICIES
CREATE POLICY "Parties or group members can view payment records"
ON public.payment_records FOR SELECT
TO authenticated
USING (
    from_user = auth.uid() OR to_user = auth.uid() OR
    EXISTS (
        SELECT 1 FROM public.contributions c
        JOIN public.expenses e ON e.id = c.expense_id
        WHERE c.id = contribution_id AND public.is_group_member(e.group_id, auth.uid())
    )
);

CREATE POLICY "Claimant can insert payment record"
ON public.payment_records FOR INSERT
TO authenticated
WITH CHECK (from_user = auth.uid());

CREATE POLICY "Receiver can update payment record"
ON public.payment_records FOR UPDATE
TO authenticated
USING (to_user = auth.uid())
WITH CHECK (to_user = auth.uid());

-- ACTIVITY LOGS POLICIES
CREATE POLICY "Members can view group activity logs"
ON public.activity_logs FOR SELECT
TO authenticated
USING (public.is_group_member(group_id, auth.uid()));

CREATE POLICY "Members can insert activity logs"
ON public.activity_logs FOR INSERT
TO authenticated
WITH CHECK (public.is_group_member(group_id, auth.uid()));

-- NOTIFICATIONS POLICIES
CREATE POLICY "Users can view and update own notifications"
ON public.notifications FOR ALL
TO authenticated
USING (user_id = auth.uid())
WITH CHECK (user_id = auth.uid());

-- ============================================================
-- RPC FUNCTIONS
-- ============================================================

-- RPC: create_group
CREATE OR REPLACE FUNCTION public.create_group(p_name TEXT, p_description TEXT DEFAULT NULL)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_group_id UUID;
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    INSERT INTO public.groups (name, description, owner_id)
    VALUES (trim(p_name), p_description, v_user_id)
    RETURNING id INTO v_group_id;

    INSERT INTO public.group_members (group_id, user_id, role, status)
    VALUES (v_group_id, v_user_id, 'owner', 'active');

    INSERT INTO public.activity_logs (group_id, actor_id, entity_type, entity_id, action, new_value)
    VALUES (v_group_id, v_user_id, 'group', v_group_id, 'created', jsonb_build_object('name', p_name));

    RETURN v_group_id;
END;
$$;

-- RPC: create_expense_with_shares
CREATE OR REPLACE FUNCTION public.create_expense_with_shares(
    p_group_id UUID,
    p_title TEXT,
    p_amount_paise BIGINT,
    p_category TEXT,
    p_paid_by UUID,
    p_participants JSONB,
    p_description TEXT DEFAULT NULL,
    p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_expense_id UUID;
    v_creator_id UUID := auth.uid();
    v_item JSONB;
    v_user_id UUID;
    v_share BIGINT;
    v_total_shares BIGINT := 0;
    v_participant_ids UUID[] := ARRAY[]::UUID[];
BEGIN
    IF v_creator_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF NOT public.is_group_member(p_group_id, v_creator_id) THEN
        RAISE EXCEPTION 'Caller is not an active group member';
    END IF;

    IF NOT public.is_group_member(p_group_id, p_paid_by) THEN
        RAISE EXCEPTION 'Payer is not an active member of this group';
    END IF;

    IF p_amount_paise <= 0 THEN
        RAISE EXCEPTION 'Expense amount must be positive';
    END IF;

    IF p_participants IS NULL OR jsonb_array_length(p_participants) = 0 THEN
        RAISE EXCEPTION 'At least one participant is required';
    END IF;

    -- Pre-validate all participants before creating any records
    FOR v_item IN SELECT * FROM jsonb_array_elements(p_participants)
    LOOP
        v_user_id := (v_item->>'user_id')::UUID;
        v_share := (v_item->>'share_paise')::BIGINT;

        IF v_share < 0 THEN
            RAISE EXCEPTION 'Participant share cannot be negative';
        END IF;

        IF NOT public.is_group_member(p_group_id, v_user_id) THEN
            RAISE EXCEPTION 'Participant % is not an active member of this group', v_user_id;
        END IF;

        IF v_user_id = ANY(v_participant_ids) THEN
            RAISE EXCEPTION 'Duplicate participant detected: %', v_user_id;
        END IF;

        v_participant_ids := array_append(v_participant_ids, v_user_id);
        v_total_shares := v_total_shares + v_share;
    END LOOP;

    -- Strict check: sum of shares must equal total amount exactly
    IF v_total_shares != p_amount_paise THEN
        RAISE EXCEPTION 'Sum of participant shares (%) does not equal total expense amount (%)', v_total_shares, p_amount_paise;
    END IF;

    -- Create Expense record
    INSERT INTO public.expenses (
        group_id, title, description, amount_paise, category, paid_by, created_by, notes
    ) VALUES (
        p_group_id, trim(p_title), p_description, p_amount_paise, p_category, p_paid_by, v_creator_id, p_notes
    ) RETURNING id INTO v_expense_id;

    -- Insert participants & contributions
    FOR v_item IN SELECT * FROM jsonb_array_elements(p_participants)
    LOOP
        v_user_id := (v_item->>'user_id')::UUID;
        v_share := (v_item->>'share_paise')::BIGINT;

        -- Record participant share
        INSERT INTO public.expense_participants (expense_id, user_id, share_paise)
        VALUES (v_expense_id, v_user_id, v_share);

        -- If participant is NOT the payer, create contribution obligation
        IF v_user_id != p_paid_by AND v_share > 0 THEN
            INSERT INTO public.contributions (expense_id, user_id, share_paise, committed_paise, status)
            VALUES (v_expense_id, v_user_id, v_share, 0, 'will_pay');

            -- Send in-app notification to participant
            INSERT INTO public.notifications (user_id, group_id, type, title, body, payload)
            VALUES (
                v_user_id,
                p_group_id,
                'new_expense',
                'New Expense Added',
                format('You were added to "%s". Suggested share: ₹%s', p_title, (v_share::numeric / 100)::text),
                jsonb_build_object('expense_id', v_expense_id, 'amount_paise', v_share)
            );
        END IF;
    END LOOP;

    -- Audit log
    INSERT INTO public.activity_logs (group_id, actor_id, entity_type, entity_id, action, new_value)
    VALUES (p_group_id, v_creator_id, 'expense', v_expense_id, 'created', jsonb_build_object('title', p_title, 'amount_paise', p_amount_paise));

    RETURN v_expense_id;
END;
$$;

-- RPC: confirm_payment_record
CREATE OR REPLACE FUNCTION public.confirm_payment_record(
    p_payment_record_id UUID,
    p_action TEXT, -- 'confirm' or 'dispute'
    p_dispute_reason TEXT DEFAULT NULL
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_caller_id UUID := auth.uid();
    v_pay_record RECORD;
    v_contribution RECORD;
    v_expense RECORD;
    v_confirmed_sum BIGINT := 0;
BEGIN
    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_pay_record
    FROM public.payment_records
    WHERE id = p_payment_record_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Payment record not found';
    END IF;

    IF v_pay_record.to_user != v_caller_id THEN
        RAISE EXCEPTION 'Only the payment recipient can confirm or dispute this payment';
    END IF;

    IF v_pay_record.status = 'paid_confirmed' THEN
        RAISE EXCEPTION 'Payment record has already been confirmed';
    END IF;

    SELECT * INTO v_contribution
    FROM public.contributions
    WHERE id = v_pay_record.contribution_id
    FOR UPDATE;

    SELECT * INTO v_expense
    FROM public.expenses
    WHERE id = v_contribution.expense_id;

    IF p_action = 'confirm' THEN
        UPDATE public.payment_records
        SET status = 'paid_confirmed',
            responded_at = NOW(),
            responded_by = v_caller_id
        WHERE id = p_payment_record_id;

        -- Compute cumulative confirmed payments for this contribution
        SELECT COALESCE(SUM(amount_paise), 0)::BIGINT INTO v_confirmed_sum
        FROM public.payment_records
        WHERE contribution_id = v_contribution.id AND status = 'paid_confirmed';

        -- If confirmed total satisfies obligation, mark contribution confirmed, otherwise leave will_pay with updated committed amount
        IF v_confirmed_sum >= v_contribution.share_paise THEN
            UPDATE public.contributions
            SET status = 'paid_confirmed',
                committed_paise = v_confirmed_sum,
                updated_at = NOW()
            WHERE id = v_contribution.id;
        ELSE
            UPDATE public.contributions
            SET status = 'will_pay',
                committed_paise = v_confirmed_sum,
                updated_at = NOW()
            WHERE id = v_contribution.id;
        END IF;

        INSERT INTO public.activity_logs (group_id, actor_id, entity_type, entity_id, action, new_value)
        VALUES (v_expense.group_id, v_caller_id, 'payment_record', p_payment_record_id, 'confirmed', jsonb_build_object('amount_paise', v_pay_record.amount_paise));

        INSERT INTO public.notifications (user_id, group_id, type, title, body, payload)
        VALUES (
            v_pay_record.from_user,
            v_expense.group_id,
            'payment_confirmed',
            'Payment Confirmed!',
            format('Your payment of ₹%s was confirmed.', (v_pay_record.amount_paise::numeric / 100)::text),
            jsonb_build_object('payment_id', p_payment_record_id)
        );

        RETURN TRUE;

    ELSIF p_action = 'dispute' THEN
        IF p_dispute_reason IS NULL OR trim(p_dispute_reason) = '' THEN
            RAISE EXCEPTION 'Dispute reason is mandatory';
        END IF;

        UPDATE public.payment_records
        SET status = 'disputed',
            responded_at = NOW(),
            responded_by = v_caller_id,
            dispute_reason = trim(p_dispute_reason)
        WHERE id = p_payment_record_id;

        UPDATE public.contributions
        SET status = 'disputed',
            updated_at = NOW()
        WHERE id = v_contribution.id;

        INSERT INTO public.activity_logs (group_id, actor_id, entity_type, entity_id, action, new_value)
        VALUES (v_expense.group_id, v_caller_id, 'payment_record', p_payment_record_id, 'disputed', jsonb_build_object('reason', p_dispute_reason));

        INSERT INTO public.notifications (user_id, group_id, type, title, body, payload)
        VALUES (
            v_pay_record.from_user,
            v_expense.group_id,
            'payment_disputed',
            'Payment Disputed',
            format('Your payment claim was disputed: %s', trim(p_dispute_reason)),
            jsonb_build_object('payment_id', p_payment_record_id)
        );

        RETURN TRUE;
    ELSE
        RAISE EXCEPTION 'Invalid action: must be confirm or dispute';
    END IF;
END;
$$;

-- RPC: compute_group_balances
CREATE OR REPLACE FUNCTION public.compute_group_balances(p_group_id UUID)
RETURNS TABLE (
    user_id UUID,
    display_name TEXT,
    net_balance_paise BIGINT
)
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
BEGIN
    RETURN QUERY
    WITH member_profiles AS (
        SELECT gm.user_id, p.display_name
        FROM public.group_members gm
        JOIN public.profiles p ON p.id = gm.user_id
        WHERE gm.group_id = p_group_id AND gm.status = 'active'
    ),
    paid_amounts AS (
        SELECT e.paid_by AS user_id, COALESCE(SUM(e.amount_paise), 0)::BIGINT AS total_paid
        FROM public.expenses e
        WHERE e.group_id = p_group_id AND e.status = 'active'
        GROUP BY e.paid_by
    ),
    share_amounts AS (
        SELECT ep.user_id, COALESCE(SUM(ep.share_paise), 0)::BIGINT AS total_share
        FROM public.expense_participants ep
        JOIN public.expenses e ON e.id = ep.expense_id
        WHERE e.group_id = p_group_id AND e.status = 'active'
        GROUP BY ep.user_id
    ),
    settled_received AS (
        SELECT pr.to_user AS user_id, COALESCE(SUM(pr.amount_paise), 0)::BIGINT AS total_received
        FROM public.payment_records pr
        JOIN public.contributions c ON c.id = pr.contribution_id
        JOIN public.expenses e ON e.id = c.expense_id
        WHERE e.group_id = p_group_id AND pr.status = 'paid_confirmed'
        GROUP BY pr.to_user
    ),
    settled_sent AS (
        SELECT pr.from_user AS user_id, COALESCE(SUM(pr.amount_paise), 0)::BIGINT AS total_sent
        FROM public.payment_records pr
        JOIN public.contributions c ON c.id = pr.contribution_id
        JOIN public.expenses e ON e.id = c.expense_id
        WHERE e.group_id = p_group_id AND pr.status = 'paid_confirmed'
        GROUP BY pr.from_user
    )
    SELECT
        mp.user_id,
        mp.display_name,
        (
            COALESCE(pa.total_paid, 0)
            - COALESCE(sa.total_share, 0)
            - COALESCE(sr.total_received, 0)
            + COALESCE(ss.total_sent, 0)
        )::BIGINT AS net_balance_paise
    FROM member_profiles mp
    LEFT JOIN paid_amounts pa ON pa.user_id = mp.user_id
    LEFT JOIN share_amounts sa ON sa.user_id = mp.user_id
    LEFT JOIN settled_received sr ON sr.user_id = mp.user_id
    LEFT JOIN settled_sent ss ON ss.user_id = mp.user_id
    ORDER BY net_balance_paise DESC;
END;
$$;

-- RPC: join_group_by_invite
CREATE OR REPLACE FUNCTION public.join_group_by_invite(p_code TEXT)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_invite RECORD;
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    SELECT * INTO v_invite
    FROM public.group_invites
    WHERE code = upper(trim(p_code)) AND status = 'active'
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Invalid or expired invite code';
    END IF;

    IF v_invite.expires_at IS NOT NULL AND v_invite.expires_at < NOW() THEN
        UPDATE public.group_invites SET status = 'expired' WHERE id = v_invite.id;
        RAISE EXCEPTION 'Invite code has expired';
    END IF;

    IF v_invite.max_uses IS NOT NULL AND v_invite.uses >= v_invite.max_uses THEN
        UPDATE public.group_invites SET status = 'expired' WHERE id = v_invite.id;
        RAISE EXCEPTION 'Invite code limit reached';
    END IF;

    -- Upsert membership
    INSERT INTO public.group_members (group_id, user_id, role, status)
    VALUES (v_invite.group_id, v_user_id, 'member', 'active')
    ON CONFLICT (group_id, user_id)
    DO UPDATE SET status = 'active';

    UPDATE public.group_invites SET uses = uses + 1 WHERE id = v_invite.id;

    INSERT INTO public.activity_logs (group_id, actor_id, entity_type, entity_id, action)
    VALUES (v_invite.group_id, v_user_id, 'group_member', v_user_id, 'joined');

    RETURN v_invite.group_id;
END;
$$;

-- Alias for join_group_by_code
CREATE OR REPLACE FUNCTION public.join_group_by_code(p_code TEXT)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    RETURN public.join_group_by_invite(p_code);
END;
$$;

-- RPC: get_payee_upi
CREATE OR REPLACE FUNCTION public.get_payee_upi(p_payee_id UUID)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
DECLARE
    v_caller_id UUID := auth.uid();
    v_has_obligation BOOLEAN;
    v_upi TEXT;
BEGIN
    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF v_caller_id = p_payee_id THEN
        SELECT upi_id INTO v_upi FROM public.profiles WHERE id = p_payee_id;
        RETURN v_upi;
    END IF;

    -- Check if caller owes p_payee_id in any shared group
    SELECT EXISTS (
        SELECT 1 FROM public.contributions c
        JOIN public.expenses e ON e.id = c.expense_id
        WHERE c.user_id = v_caller_id
          AND e.paid_by = p_payee_id
          AND e.status = 'active'
          AND c.status IN ('will_pay', 'paid_pending_confirmation', 'disputed')
    ) INTO v_has_obligation;

    IF NOT v_has_obligation THEN
        RAISE EXCEPTION 'UPI ID access restricted: No outstanding obligation to this user.';
    END IF;

    SELECT upi_id INTO v_upi FROM public.profiles WHERE id = p_payee_id AND upi_consent = TRUE;
    RETURN v_upi;
END;
$$;
