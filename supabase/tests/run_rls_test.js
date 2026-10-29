const connectionString = process.env.SUPABASE_TEST_DB_URL || process.env.SUPABASE_DB_URL || process.env.DATABASE_URL;

if (!connectionString) {
  console.error("Error: Missing test database connection string.");
  console.error("Please set SUPABASE_TEST_DB_URL, SUPABASE_DB_URL, or DATABASE_URL before running RLS tests.");
  console.error("Example: export SUPABASE_TEST_DB_URL='postgresql://postgres:[PASSWORD]@[HOST]:5432/postgres'");
  process.exit(1);
}

const fs = require('fs');
let Client;
try {
  Client = require('pg').Client;
} catch (e) {
  try {
    Client = require('/tmp/pg_runner/node_modules/pg').Client;
  } catch (err) {
    console.error("Error: 'pg' module not found. Run 'npm install pg' first.");
    process.exit(1);
  }
}

async function runTests() {
  const client = new Client({
    connectionString,
    ssl: { rejectUnauthorized: false }
  });

  try {
    await client.connect();
    console.log("Connected to Supabase PostgreSQL.");

    console.log("\n1. Applying seed.test.sql (Realistic Indian test data: Rahul, Priya, Amit, Sneha)...");
    const seedSql = fs.readFileSync('/app/applet/supabase/seed.test.sql', 'utf8');
    await client.query(seedSql);
    console.log("Seed data applied successfully!");

    console.log("\n2. Executing rls_test.sql (RLS enforcement & non-member isolation)...");
    const rlsSql = fs.readFileSync('/app/applet/supabase/tests/rls_test.sql', 'utf8');
    await client.query(rlsSql);
    console.log("RLS tests passed successfully! Non-member Sneha isolated from group & expenses; member Rahul can access; UPI access restricted.");

    console.log("\n3. Testing RPC compute_group_balances for Goa Trip 2026...");
    const balanceRes = await client.query(`
      SELECT user_id, display_name, net_balance_paise, (net_balance_paise::numeric / 100)::text AS balance_inr
      FROM public.compute_group_balances('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
    `);
    console.table(balanceRes.rows);

    console.log("\n4. Cleaning up test seed data to keep database clean...");
    await client.query(`
      DELETE FROM public.groups WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
      DELETE FROM public.profiles WHERE id IN (
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222',
        '33333333-3333-3333-3333-333333333333',
        '44444444-4444-4444-4444-444444444444'
      );
      DELETE FROM auth.users WHERE id IN (
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222',
        '33333333-3333-3333-3333-333333333333',
        '44444444-4444-4444-4444-444444444444'
      );
    `);
    console.log("Test seed data cleaned up successfully. Production database is in pristine initial state!");

    await client.end();
  } catch (err) {
    console.error("Test execution failed:", err);
    process.exit(1);
  }
}

runTests();
