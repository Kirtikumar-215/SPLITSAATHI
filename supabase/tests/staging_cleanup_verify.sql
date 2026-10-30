-- SPLITSAATHI STAGING CLEANUP VERIFICATION
-- Run this in your Supabase SQL Editor after running staging_security_suite.sql.
-- Expected output: all counts MUST be 0.

SELECT
    (SELECT count(*) FROM auth.users WHERE email LIKE 'staging_%') AS leftover_auth_users,
    (SELECT count(*) FROM public.profiles WHERE display_name LIKE '%Staging%') AS leftover_profiles,
    (SELECT count(*) FROM public.groups WHERE name = 'Weekend Staging Trip') AS leftover_groups,
    (SELECT count(*) FROM public.expenses WHERE title = 'Staging Team Dinner') AS leftover_expenses,
    (SELECT count(*) FROM public.payment_records WHERE upi_reference LIKE 'UPI-REF-%') AS leftover_payments;
