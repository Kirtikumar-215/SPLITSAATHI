const path = require('path');
const fs = require('fs');

const connectionString = process.env.SUPABASE_DB_URL || process.env.DATABASE_URL;

if (!connectionString) {
  console.error("Error: Missing database connection string.");
  console.error("Please set SUPABASE_DB_URL or DATABASE_URL in your environment before running this script.");
  console.error("Example: export SUPABASE_DB_URL='postgresql://postgres:[PASSWORD]@[HOST]:5432/postgres'");
  process.exit(1);
}

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

    const migrationsDir = path.join(__dirname, 'migrations');
    const files = fs.readdirSync(migrationsDir)
      .filter(f => f.endsWith('.sql'))
      .sort();

    console.log(`Found ${files.length} migration files in order:`, files);
    for (const file of files) {
      const filePath = path.join(migrationsDir, file);
      const sql = fs.readFileSync(filePath, 'utf8');
      console.log(`\nApplying migration: ${file}...`);
      await client.query(sql);
      console.log(`Successfully applied: ${file}`);
    }

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
    const sanitizedMsg = (err && err.message ? err.message : String(err))
      .replace(/:\/\/[^:]+:[^@]+@/, '://[REDACTED_USER]:[REDACTED_PASSWORD]@');
    console.error("Migration failed:", sanitizedMsg);
    process.exit(1);
  }
}

applyMigration();
