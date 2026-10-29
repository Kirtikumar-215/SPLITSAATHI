const connectionString = process.env.SUPABASE_DB_URL || process.env.DATABASE_URL;

if (!connectionString) {
  console.error("Error: Missing database connection string.");
  console.error("Please set SUPABASE_DB_URL or DATABASE_URL in your environment before running this script.");
  console.error("Example: export SUPABASE_DB_URL='postgresql://postgres:[PASSWORD]@[HOST]:5432/postgres'");
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

async function applyMigration() {
  const client = new Client({
    connectionString,
    ssl: { rejectUnauthorized: false }
  });

  try {
    await client.connect();
    console.log("Connected to Supabase PostgreSQL.");

    const sql = fs.readFileSync('/app/applet/supabase/migrations/20261027000001_initial_schema.sql', 'utf8');

    console.log("Applying migration 20261027000001_initial_schema.sql...");
    await client.query(sql);
    console.log("Migration executed successfully!");

    console.log("\n--- PUBLIC TABLES & ROW LEVEL SECURITY STATUS ---");
    const tablesRes = await client.query(`
      SELECT tablename, rowsecurity
      FROM pg_tables
      WHERE schemaname = 'public'
      ORDER BY tablename;
    `);
    console.table(tablesRes.rows);

    console.log("\n--- RLS POLICIES ---");
    const policiesRes = await client.query(`
      SELECT tablename, policyname, cmd, permissive
      FROM pg_policies
      WHERE schemaname = 'public'
      ORDER BY tablename, policyname;
    `);
    console.table(policiesRes.rows);

    console.log("\n--- RPC FUNCTIONS IN PUBLIC SCHEMA ---");
    const rpcRes = await client.query(`
      SELECT routine_name, routine_type
      FROM information_schema.routines
      WHERE routine_schema = 'public'
      ORDER BY routine_name;
    `);
    console.table(rpcRes.rows);

    await client.end();
  } catch (err) {
    console.error("Migration failed:", err);
    process.exit(1);
  }
}

applyMigration();
