/** Shared field bounds and format rules for auth forms. */

export const USERNAME_MIN = 3
export const USERNAME_MAX = 30

export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 64

export const SLUG_MIN = 3
export const SLUG_MAX = 40

/** Slug-friendly: lowercase alphanumeric plus '-' and '_'. */
export const SLUG_REGEX = /^[a-z0-9_-]+$/

/**
 * Allowed password characters: alphanumeric plus the special set
 * ~!@#$%^&*()_+={}|[]:;"'<,>.?/ - only ']' needs escaping inside the class.
 */
export const PASSWORD_REGEX = /^[A-Za-z0-9~!@#$%^&*()_+={}|[\]:;"'<,>.?/]+$/

/** Avatar target dimensions after in-browser processing. */
export const AVATAR_SIZE = 256

/**
 * Additional-contact entry bounds (shared by the schema and the widget).
 * `VALUE_MAX` is 128 rather than 64 because the link types (website, LinkedIn,
 * ...) store full URLs - a real LinkedIn profile URL already runs ~50 chars
 * before anyone adds a query string.
 */
export const CONTACT_DESC_MAX = 32
export const CONTACT_VALUE_MAX = 128
export const CONTACT_MAX_ENTRIES = 15

/** Post & Comment bounds*/
export const POST_MAX = 5000
export const COMMENT_MAX = 2000

/** CV upload rules (jobs apply flow — shared by the mock service and the UI). */
export const CV_MAX_BYTES = 5 * 1024 * 1024
export const CV_ACCEPTED_TYPES: readonly string[] = ['application/pdf']
export const CV_ACCEPTED_EXT: readonly string[] = ['.pdf']

/** Job posting bounds  */
export const JOB_TITLE_MIN = 3
export const JOB_TITLE_MAX = 120
export const JOB_DESCRIPTION_MIN = 40
export const JOB_DESCRIPTION_MAX = 5000
export const JOB_LOCATION_MAX = 120
export const JOB_KEYWORDS_MAX = 10
export const JOB_KEYWORD_MAX = 32
export const JOB_EXTERNAL_URL_MAX = 300
