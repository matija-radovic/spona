// Mirrors _mediaqueries.scss into a ts file, or with --check fails when that file is stale

import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const SOURCE = fileURLToPath(new URL('../src/shared/css/_mediaqueries.scss', import.meta.url))
const TARGET = fileURLToPath(new URL('../src/shared/css/mediaqueries.ts', import.meta.url))

const SKIP = new Set(['ie-specific'])

const camelCase = (name) => name.replace(/-([a-z])/g, (_, letter) => letter.toUpperCase())

const scss = readFileSync(SOURCE, 'utf8')
const entries = [...scss.matchAll(/^\$([\w-]+):\s*'([^']+)';/gm)]
  .filter(([, name]) => !SKIP.has(name))
  .map(([, name, query]) => `  ${camelCase(name)}: '${query}',`)

if (entries.length === 0) {
  console.error('gen-mediaqueries: no $variables found in _mediaqueries.scss')
  process.exit(1)
}

const file = `// Generated from _mediaqueries.scss - do not edit.
// Add or change a breakpoint in scss, then run \`npm run gen:mediaqueries\`.

export const MQ = {
${entries.join('\n')}
} as const

export type MediaQueryName = keyof typeof MQ
`

if (process.argv.includes('--check')) {
  const current = existsSync(TARGET) ? readFileSync(TARGET, 'utf8').replace(/\r\n/g, '\n') : ''
  if (current !== file) {
    console.error('gen-mediaqueries: mediaqueries.ts is out of date, run `npm run gen:mediaqueries` and commit it')
    process.exit(1)
  }
  console.log('gen-mediaqueries: mediaqueries.ts matches _mediaqueries.scss')
  process.exit(0)
}

writeFileSync(TARGET, file)
console.log(`gen-mediaqueries: wrote ${entries.length} queries to src/shared/css/mediaqueries.ts`)
