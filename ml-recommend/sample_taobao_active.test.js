const assert = require('assert');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const root = path.resolve(__dirname, '..');
const outputDir = path.join(root, 'ml-recommend', 'test-output', 'active-sample');
const inputPath = path.join(outputDir, 'UserBehavior.active.csv');
const outputPath = path.join(outputDir, 'sampled_events.csv');

fs.rmSync(outputDir, { recursive: true, force: true });
fs.mkdirSync(outputDir, { recursive: true });

const rows = [
  ['u1', 'i1', 'c1', 'pv', '100'],
  ['u2', 'i2', 'c2', 'pv', '110'],
  ['u2', 'i3', 'c2', 'cart', '120'],
  ['u2', 'i4', 'c2', 'buy', '130'],
  ['u3', 'i5', 'c3', 'pv', '140'],
  ['u3', 'i6', 'c3', 'fav', '150'],
  ['u3', 'i7', 'c3', 'cart', '160'],
  ['u3', 'i8', 'c3', 'buy', '170'],
  ['u4', 'i9', 'c4', 'pv', '180'],
];

fs.writeFileSync(inputPath, rows.map((row) => row.join(',')).join('\n'), 'utf8');

execFileSync('python', [
  path.join(root, 'ml-recommend', 'sample_taobao.py'),
  '--input', inputPath,
  '--output-dir', outputDir,
  '--max-users', '2',
  '--max-items', '20',
  '--max-events', '20',
  '--start-ts', '0',
  '--end-ts', '1000',
  '--strategy', 'active',
], { cwd: root, stdio: 'pipe' });

const output = fs.readFileSync(outputPath, 'utf8').trim().split(/\r?\n/).slice(1);
const sampledUsers = [...new Set(output.map((line) => line.split(',')[0]))].sort();

assert.deepStrictEqual(sampledUsers, ['u2', 'u3']);
assert.strictEqual(output.length, 7);

console.log('active taobao sampling checks passed');
