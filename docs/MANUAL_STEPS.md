# Manual Steps for SplitSaathi

## 1. Remote Supabase Setup (Optional for Cloud Sync)
If you wish to link SplitSaathi to your own Supabase project:
1. Log in to your [Supabase Dashboard](https://supabase.com).
2. Create a new project (e.g. `splitsaathi-prod`).
3. Under **SQL Editor**, run the migration script: `supabase/migrations/20261027000001_initial_schema.sql`.
4. Copy your `Project URL` and `anon key` from **Project Settings > API**.
5. In AI Studio, add these to the Secrets panel or `.env`:
   ```env
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_ANON_KEY=your-anon-public-key
   ```
*(Note: SplitSaathi comes with full offline-first Room local persistence and seed accounts out of the box, so you can test all features right now in the emulator without configuring remote Supabase!)*

## 2. Generating the Android APK
The APK can be downloaded directly from the AI Studio **Download / Export APK** menu, or built via Gradle / EAS Build as documented in `docs/BUILD_APK.md`.
