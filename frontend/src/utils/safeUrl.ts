// Turns an untrusted string into an href that is safe to put on an <a> (or any
// other navigable attribute), or null when it isn't one.
//
// The rule is ALLOWLIST, never blocklist - a blocklist loses to the next
// encoding trick, an allowlist doesn't.

/** Schemes that may appear in an href. Anything else resolves to null. */
export const SAFE_PROTOCOLS: readonly string[] = ['http:', 'https:', 'mailto:', 'tel:']

/** Web-navigable only - use for fields that must be a page, not an action. */
export const WEB_PROTOCOLS: readonly string[] = ['http:', 'https:']

const isInvisible = (code: number): boolean =>
  code <= 0x1f ||
  (code >= 0x7f && code <= 0x9f) ||
  (code >= 0x200b && code <= 0x200d) ||
  code === 0x2028 ||
  code === 0x2029 ||
  code === 0xfeff

const stripInvisible = (value: string): string =>
  Array.from(value)
    .filter((char) => !isInvisible(char.codePointAt(0) ?? 0))
    .join('')

/** `scheme:` per RFC 3986 - letter, then letters/digits/`+`/`-`/`.`. */
const HAS_SCHEME = /^[a-z][a-z0-9+.-]*:/i

/** Same-document or same-origin target: `#x`, `?x`, `/x` - but NOT `//host`. */
const SAME_DOCUMENT = /^(?:[#?]|\/(?!\/))/

/** Bare host with a dot and no scheme, e.g. `instagram.com/foo`. */
const LOOKS_LIKE_HOST = /^[^\s/?#@:]+\.[^\s/?#@:]{2,}(?:[/?#]|$)/

interface SafeHrefOptions {
  /** Allowed schemes. Defaults to {@link SAFE_PROTOCOLS}. */
  protocols?: readonly string[]
  /**
   * Treat a bare `example.com/x` as `https://example.com/x`. On by default -
   * humans type contact links without a scheme. Turn OFF when the caller
   * already built a complete URL (a `tel:`/`mailto:` it assembled itself).
   */
  allowSchemeless?: boolean
  /**
   * Let `#x` / `?x` / `/x` through untouched. On by default so in-app and
   * placeholder links (the footer's `#`) survive. `//host` is always rejected -
   * it reads as same-origin but navigates away.
   */
  allowRelative?: boolean
}

/**
 * The sanitized href, or `null` when the input cannot be made safe. A `null`
 * means "render as plain text" - never "render it anyway".
 *
 * The returned string is `URL`-normalized (percent-encoded, scheme lowercased),
 * so it is the value to put in the attribute - but NOT the value to show. Keep
 * displaying the original string, so the link text and its target can never
 * disagree.
 */
export const safeHref = (
  raw: string | null | undefined,
  { protocols = SAFE_PROTOCOLS, allowSchemeless = true, allowRelative = true }: SafeHrefOptions = {},
): string | null => {
  if (!raw) return null

  const cleaned = stripInvisible(raw).trim()
  if (cleaned === '') return null

  if (SAME_DOCUMENT.test(cleaned)) return allowRelative ? cleaned : null

  let candidate = cleaned
  if (!HAS_SCHEME.test(candidate)) {
    // No scheme at all. Only prefix when it actually looks like a host -
    // blindly prefixing turns junk (and a de-fanged `javascript:x`) into a
    // plausible URL.
    if (!allowSchemeless || !LOOKS_LIKE_HOST.test(candidate)) return null
    candidate = `https://${candidate}`
  }

  let url: URL
  try {
    url = new URL(candidate)
  } catch {
    return null
  }

  return protocols.includes(url.protocol) ? url.href : null
}

/** True when `raw` yields a usable href - for gating UI without building one. */
export const isSafeHref = (raw: string | null | undefined, options?: SafeHrefOptions): boolean =>
  safeHref(raw, options) !== null

/**
 * `https://www.example.com/page/` -> `example.com/page`. Display helper only -
 * the shortened text still comes from the same string the href was built from.
 */
export const displayUrl = (raw: string): string =>
  stripInvisible(raw)
    .trim()
    .replace(/^[a-z][a-z0-9+.-]*:\/\//i, '')
    .replace(/^www\./i, '')
    .replace(/\/$/, '')
