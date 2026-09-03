/** `null` = as myself; otherwise the organization's actor id. */
export type PostAsValue = string | null

export const PostAsSurface = Object.freeze({
  CONTENT: 'content',
  JOB: 'job',
} as const)

export type PostAsSurface = (typeof PostAsSurface)[keyof typeof PostAsSurface]
