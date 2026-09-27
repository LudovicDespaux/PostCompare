const fs = require('node:fs');
const { execFileSync } = require('node:child_process');
const commit = process.env.GITHUB_SHA || execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
if (!/^[0-9a-f]{40}$/.test(commit)) throw new Error('Invalid build revision');
fs.writeFileSync('frontend/dist/version.json', JSON.stringify({ commit }) + '\n');
