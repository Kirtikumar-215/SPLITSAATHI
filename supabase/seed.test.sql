-- SplitSaathi Test Seed Data (Realistic Indian Test Data for Integration Testing)
-- Users: Rahul, Priya, Amit, Sneha (Sneha is not in the group to verify RLS isolation)
-- Group: Goa Trip 2026
-- Expenses: ₹1,850 dinner, ₹4,200 cab (settled and unsettled states)

-- 1. AUTH USERS
INSERT INTO auth.users (id, email)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'rahul@example.com'),
    ('22222222-2222-2222-2222-222222222222', 'priya@example.com'),
    ('33333333-3333-3333-3333-333333333333', 'amit@example.com'),
    ('44444444-4444-4444-4444-444444444444', 'sneha@example.com')
ON CONFLICT (id) DO NOTHING;

-- 2. PROFILES
INSERT INTO public.profiles (id, display_name, phone, email, avatar_key, upi_id, upi_consent, upi_consent_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Rahul Sharma', '9876543210', 'rahul@example.com', 'avatar_1', 'rahul@okaxis', TRUE, NOW()),
    ('22222222-2222-2222-2222-222222222222', 'Priya Verma', '9876543211', 'priya@example.com', 'avatar_2', 'priya@okhdfcbank', TRUE, NOW()),
    ('33333333-3333-3333-3333-333333333333', 'Amit Shah', '9876543212', 'amit@example.com', 'avatar_3', 'amit@oksbi', TRUE, NOW()),
    ('44444444-4444-4444-4444-444444444444', 'Sneha Reddy', '9876543213', 'sneha@example.com', 'avatar_4', 'sneha@icici', TRUE, NOW())
ON CONFLICT (id) DO NOTHING;

-- 3. GROUP: Goa Trip 2026
INSERT INTO public.groups (id, name, description, owner_id)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'Goa Trip 2026',
    'Friends Goa vacation expenses',
    '11111111-1111-1111-1111-111111111111'
) ON CONFLICT (id) DO NOTHING;

-- 4. MEMBERS (Rahul, Priya, Amit; Sneha is NOT added to test RLS)
INSERT INTO public.group_members (group_id, user_id, role, status)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'owner', 'active'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '22222222-2222-2222-2222-222222222222', 'member', 'active'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '33333333-3333-3333-3333-333333333333', 'member', 'active')
ON CONFLICT (group_id, user_id) DO NOTHING;

-- 5. GROUP INVITE CODE: GOA123
INSERT INTO public.group_invites (group_id, code, created_by, max_uses, uses, status)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'GOA123',
    '11111111-1111-1111-1111-111111111111',
    10,
    2,
    'active'
) ON CONFLICT (code) DO NOTHING;

-- 6. EXPENSE 1: ₹1,850 Dinner (185000 paise), Paid by Rahul
INSERT INTO public.expenses (id, group_id, title, amount_paise, category, paid_by, created_by, status)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'Beachside Seafood Dinner',
    185000,
    'Food',
    '11111111-1111-1111-1111-111111111111',
    '11111111-1111-1111-1111-111111111111',
    'active'
) ON CONFLICT (id) DO NOTHING;

-- Participants for Expense 1 (Rahul: 61666, Priya: 61667, Amit: 61667 paise)
INSERT INTO public.expense_participants (expense_id, user_id, share_paise)
VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', '11111111-1111-1111-1111-111111111111', 61666),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', '22222222-2222-2222-2222-222222222222', 61667),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', '33333333-3333-3333-3333-333333333333', 61667)
ON CONFLICT (expense_id, user_id) DO NOTHING;

-- Contributions for Expense 1:
-- Priya's contribution is SETTLED (paid_confirmed)
INSERT INTO public.contributions (id, expense_id, user_id, share_paise, committed_paise, status)
VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccc01',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01',
    '22222222-2222-2222-2222-222222222222',
    61667,
    61667,
    'paid_confirmed'
) ON CONFLICT (id) DO NOTHING;

-- Amit's contribution is UNSETTLED (will_pay)
INSERT INTO public.contributions (id, expense_id, user_id, share_paise, committed_paise, status)
VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccc02',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01',
    '33333333-3333-3333-3333-333333333333',
    61667,
    0,
    'will_pay'
) ON CONFLICT (id) DO NOTHING;

-- Payment record for Priya's settled payment to Rahul (confirmed by Rahul)
INSERT INTO public.payment_records (
    id, contribution_id, from_user, to_user, amount_paise, upi_reference, status, idempotency_key, claimed_at, responded_at, responded_by
) VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddd01',
    'cccccccc-cccc-cccc-cccc-cccccccccc01',
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    61667,
    'UPI/2026/9823412',
    'paid_confirmed',
    gen_random_uuid(),
    NOW() - INTERVAL '1 hour',
    NOW() - INTERVAL '30 minutes',
    '11111111-1111-1111-1111-111111111111'
) ON CONFLICT (id) DO NOTHING;

-- 7. EXPENSE 2: ₹4,200 Cab (420000 paise), Paid by Priya
INSERT INTO public.expenses (id, group_id, title, amount_paise, category, paid_by, created_by, status)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'Airport to Resort Cab',
    420000,
    'Travel',
    '22222222-2222-2222-2222-222222222222',
    '22222222-2222-2222-2222-222222222222',
    'active'
) ON CONFLICT (id) DO NOTHING;

-- Participants for Expense 2 (140,000 paise each)
INSERT INTO public.expense_participants (expense_id, user_id, share_paise)
VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02', '11111111-1111-1111-1111-111111111111', 140000),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02', '22222222-2222-2222-2222-222222222222', 140000),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02', '33333333-3333-3333-3333-333333333333', 140000)
ON CONFLICT (expense_id, user_id) DO NOTHING;

-- Contributions for Expense 2:
-- Rahul's contribution is UNSETTLED (paid_pending_confirmation - claimed by Rahul, awaiting Priya's confirmation)
INSERT INTO public.contributions (id, expense_id, user_id, share_paise, committed_paise, status)
VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccc03',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02',
    '11111111-1111-1111-1111-111111111111',
    140000,
    140000,
    'paid_pending_confirmation'
) ON CONFLICT (id) DO NOTHING;

-- Amit's contribution is UNSETTLED (will_pay)
INSERT INTO public.contributions (id, expense_id, user_id, share_paise, committed_paise, status)
VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccc04',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02',
    '33333333-3333-3333-3333-333333333333',
    140000,
    0,
    'will_pay'
) ON CONFLICT (id) DO NOTHING;

-- Payment record for Rahul's claimed payment to Priya
INSERT INTO public.payment_records (
    id, contribution_id, from_user, to_user, amount_paise, upi_reference, status, idempotency_key, claimed_at
) VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddd02',
    'cccccccc-cccc-cccc-cccc-cccccccccc03',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    140000,
    'UPI/2026/1029384',
    'paid_pending_confirmation',
    gen_random_uuid(),
    NOW() - INTERVAL '10 minutes'
) ON CONFLICT (id) DO NOTHING;
