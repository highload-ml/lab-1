// Removes generator output the app does not use (the ignore-file option is not honoured by the CLI wrapper).
import { rm } from 'node:fs/promises';

const unused = ['git_push.sh', 'README.md', '.gitignore', '.openapi-generator-ignore', '.openapi-generator'];
await Promise.all(unused.map((file) => rm(`src/app/api/${file}`, { recursive: true, force: true })));
