// Downloads the backend OpenAPI spec into openapi/openapi.json (the input of `npm run api:generate`).
// Usage: npm run api:fetch [-- http://localhost:8080 | path/to/spec.json]
//
// Normalization, so the generated client is stable and readable:
// - `servers` is removed: the app calls the API with relative URLs through the dev proxy / nginx.
// - springdoc makes operationIds globally unique by suffixing duplicates (`create2`, `findAll4`).
//   The suffix is replaced by a tag prefix (`Projects_create`) that the generator strips again
//   (removeOperationIdPrefix), so ProjectsService gets `create()`. Names must stay unique within a tag.
// - springdoc declares responses as `*/*`; the generator would then read every body as a Blob.
//   The backend always answers JSON (bodies and ProblemDetail), so `*/*` becomes `application/json`.
import { readFile, writeFile } from 'node:fs/promises';

const source = process.argv[2] ?? process.env.BACKEND_URL ?? 'http://localhost:8080';
const spec = source.startsWith('http') ? await download(source) : JSON.parse(await readFile(source, 'utf8'));
delete spec.servers;

const seen = new Map();
for (const [path, operations] of Object.entries(spec.paths ?? {})) {
  for (const [method, operation] of Object.entries(operations)) {
    if (!operation?.operationId) {
      continue;
    }
    const tag = tagKey(operation.tags?.[0] ?? 'default');
    // `Projects_create` (already normalized file) or `create_2` / `create2` (raw springdoc) -> `create`
    const name = operation.operationId.replace(new RegExp(`^${tag}_`), '').replace(/_?\d+$/, '');
    const key = `${tag}/${name}`;
    if (!name || seen.has(key)) {
      console.error(`Cannot name operation ${method.toUpperCase()} ${path}: '${name}' clashes with ${seen.get(key)}`);
      process.exit(1);
    }
    seen.set(key, `${method.toUpperCase()} ${path}`);
    operation.operationId = `${tag}_${name}`;
  }
}

for (const operations of Object.values(spec.paths ?? {})) {
  for (const operation of Object.values(operations)) {
    for (const response of Object.values(operation?.responses ?? {})) {
      if (response.content?.['*/*']) {
        response.content['application/json'] ??= response.content['*/*'];
        delete response.content['*/*'];
      }
    }
  }
}

await writeFile('openapi/openapi.json', JSON.stringify(spec, null, 2) + '\n');
console.log(`openapi/openapi.json updated from ${source}`);

function tagKey(tag) {
  return tag.replace(/[^A-Za-z0-9]+(.)?/g, (_, c) => (c ?? '').toUpperCase());
}

async function download(baseUrl) {
  const response = await fetch(`${baseUrl}/v3/api-docs`);
  if (!response.ok) {
    console.error(`GET ${baseUrl}/v3/api-docs failed: ${response.status}`);
    process.exit(1);
  }
  return response.json();
}
