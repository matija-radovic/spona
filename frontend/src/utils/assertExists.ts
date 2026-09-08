/**
 * Narrows a nullable value, or throws. `onMissing` is a factory rather than an
 * instance so a caller can name its own error type without allocating one on
 * every passing call.
 */
export function assertExists<T>(
  val: T | null | undefined,
  onMissing?: string | (() => Error),
): asserts val is T {
  if (val !== null && val !== undefined) return
  throw typeof onMissing === 'function' ? onMissing() : new Error(onMissing ?? 'Value does not exist')
}
