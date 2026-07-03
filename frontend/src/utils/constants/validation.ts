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

/** Additional-contact entry bounds (shared by the schema and the widget). */
export const CONTACT_DESC_MAX = 32
export const CONTACT_VALUE_MAX = 64
export const CONTACT_MAX_ENTRIES = 15

/** CV upload rules (jobs apply flow — shared by the mock service and the UI). */
export const CV_MAX_BYTES = 5 * 1024 * 1024
export const CV_ACCEPTED_TYPES: readonly string[] = ['application/pdf']
export const CV_ACCEPTED_EXT: readonly string[] = ['.pdf']
