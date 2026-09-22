// Fails when a server error code has no Serbian key under errors, or a server domain there holds a key no code serves

import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const CATALOG = fileURLToPath(
  new URL('../../backend/src/main/java/io/github/matijaradovic/spona/common/error/ErrorCode.java', import.meta.url),
)
const BUNDLE = fileURLToPath(new URL('../src/shared/lang/sr.json', import.meta.url))

if (!existsSync(CATALOG)) {
  console.warn('check-error-codes: ErrorCode.java not found beside the frontend, skipped')
  process.exit(0)
}

const codes = [...readFileSync(CATALOG, 'utf8').matchAll(/^\s+[A-Z][A-Z0-9_]*\("([a-z][\w.]*)"/gm)].map(
  ([, code]) => code,
)
if (codes.length === 0) {
  console.error('check-error-codes: no codes found in ErrorCode.java')
  process.exit(1)
}

const errors = JSON.parse(readFileSync(BUNDLE, 'utf8')).errors ?? {}
const lookup = (code) => code.split('.').reduce((node, segment) => node?.[segment], errors)
const leaves = (node, prefix) =>
  Object.entries(node).flatMap(([key, value]) =>
    typeof value === 'string' ? [`${prefix}${key}`] : leaves(value, `${prefix}${key}.`),
  )

const served = new Set(codes)
const domains = new Set(codes.map((code) => code.split('.')[0]))
const missing = codes.filter((code) => typeof lookup(code) !== 'string')
const stale = [...domains]
  .flatMap((domain) => (typeof errors[domain] === 'object' ? leaves(errors[domain], `${domain}.`) : []))
  .filter((key) => !served.has(key))

missing.forEach((code) => console.error(`check-error-codes: errors.${code} is missing from sr.json`))
stale.forEach((key) => console.error(`check-error-codes: errors.${key} in sr.json matches no server code`))
if (missing.length > 0 || stale.length > 0) process.exit(1)

console.log(`check-error-codes: all ${codes.length} server codes have a Serbian message`)
