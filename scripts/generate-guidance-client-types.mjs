#!/usr/bin/env node
import { compile } from 'json-schema-to-typescript';
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const beRoot = path.resolve(here, '..');
const workspaceRoot = path.resolve(beRoot, '..');
const schemaPath = path.join(
  beRoot,
  'src/main/resources/guidance/contracts/guidance-contracts.v1.json'
);
const outputs = [
  path.join(workspaceRoot, 'AI-Fishing-FE/src/api/generated/guidance.ts'),
  path.join(workspaceRoot, 'AI-Fishing-WEB/src/lib/api/generated/guidance.ts'),
];

const BANNER = `/* eslint-disable */
/**
 * Generated from AI-Fishing-BE/src/main/resources/guidance/contracts/guidance-contracts.v1.json
 * Do not edit by hand. Run: node AI-Fishing-BE/scripts/generate-guidance-client-types.mjs
 */

`;

const check = process.argv.includes('--check');

const schema = JSON.parse(await readFile(schemaPath, 'utf8'));
const publicDefs = Object.fromEntries(
  Object.entries(schema.$defs || {}).filter(([, def]) => !def || def['x-internal'] !== true)
);
const generateRoot = {
  ...schema,
  title: 'GuidanceContractsV1',
  $defs: publicDefs,
  anyOf: Object.keys(publicDefs).map((name) => ({
    $ref: `#/$defs/${name}`,
  })),
};

const body = await compile(generateRoot, 'GuidanceContractsV1', {
  bannerComment: '',
  additionalProperties: false,
  unreachableDefinitions: true,
  cwd: path.dirname(schemaPath),
  style: { singleQuote: true },
});
const generated = BANNER + body;
const digest = createHash('sha256').update(generated).digest('hex');

let drifted = false;
for (const output of outputs) {
  const previous = await readFile(output, 'utf8').catch(() => null);
  if (check) {
    if (previous !== generated) {
      drifted = true;
      console.error(`guidance client types drifted: ${output}`);
    }
    continue;
  }
  await mkdir(path.dirname(output), { recursive: true });
  await writeFile(output, generated);
  console.log(`wrote ${path.relative(workspaceRoot, output)}`);
}

if (check && drifted) {
  console.error('re-run: node AI-Fishing-BE/scripts/generate-guidance-client-types.mjs');
  process.exit(1);
}

if (check) {
  console.log(`guidance client types match (${digest.slice(0, 12)})`);
}
