/** Every field bound the client enforces, in one file. */

export const USERNAME_MIN = 3
export const USERNAME_MAX = 30

export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 64

export const SLUG_MIN = 3
export const SLUG_MAX = 40

/** RFC 5321 caps a forward path at 254 characters. */
export const EMAIL_MAX = 254

/** Slug-friendly: lowercase alphanumeric plus '-' and '_'. */
export const SLUG_REGEX = /^[a-z0-9_-]+$/

/**
 * Allowed password characters: alphanumeric plus the special set
 * ~!@#$%^&*()_+={}|[]:;"'<,>.?/ - only ']' needs escaping inside the class.
 */
export const PASSWORD_REGEX = /^[A-Za-z0-9~!@#$%^&*()_+={}|[\]:;"'<,>.?/]+$/

/** Avatar target dimensions after in-browser processing. */
export const AVATAR_SIZE = 256

export const AVATAR_SOURCE_MAX_BYTES = 10 * 1024 * 1024
export const AVATAR_SOURCE_ACCEPTED_TYPES: readonly string[] = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/gif',
  'image/avif',
]

/** Profile fields shared by the registration wizard and the settings pane. */
export const FULL_NAME_MAX = 120
export const BIO_MAX = 500
export const LOCATION_MAX = 120

/** Organization profile fields, shared by the create form and the settings pane. */
export const ORG_DISPLAY_NAME_MAX = 120
export const ORG_LEGAL_NAME_MAX = 160
export const ORG_DESCRIPTION_MAX = 1000
export const ORG_WEBSITE_MAX = 200

/** Additional-contact entry bounds (shared by the schema and the widget). */
export const CONTACT_TYPE_MAX = 32
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

/** Salary ceiling, matching the `numeric(12,2)` the column is declared as */
export const SALARY_MAX = 9_999_999_999.99

/** One field the backend classifies, so it takes the longer of the two shapes. */
export const INVITE_IDENTIFIER_MAX = EMAIL_MAX

/** Full-text query string; longer is refused rather than truncated. */
export const SEARCH_Q_MAX = 200
