import fs from 'node:fs';
import path from 'node:path';

const envPath = path.resolve(process.cwd(), '.env');
const required = [
  'VITE_FIREBASE_API_KEY',
  'VITE_FIREBASE_AUTH_DOMAIN',
  'VITE_FIREBASE_PROJECT_ID',
  'VITE_FIREBASE_STORAGE_BUCKET',
  'VITE_FIREBASE_MESSAGING_SENDER_ID',
  'VITE_FIREBASE_APP_ID'
];

if (!fs.existsSync(envPath)) {
  console.warn('[warn] .env file not found in parent-dashboard-web. Build may still work in CI if vars are injected.');
  process.exit(0);
}

const raw = fs.readFileSync(envPath, 'utf8');
const found = Object.fromEntries(
  raw.split(/\r?\n/)
    .filter((line) => line && !line.trim().startsWith('#') && line.includes('='))
    .map((line) => {
      const i = line.indexOf('=');
      return [line.slice(0, i).trim(), line.slice(i + 1).trim()];
    })
);

const missing = required.filter((k) => !found[k]);
if (missing.length) {
  console.error('[error] Missing required Firebase env vars:');
  missing.forEach((k) => console.error(` - ${k}`));
  process.exit(1);
}

console.log('[ok] Firebase env validation passed.');
