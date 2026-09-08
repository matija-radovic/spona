export function assertExists<T>(val: T | null | undefined, message?: string): asserts val is T {
  if (val === null || val === undefined) {
    throw new Error(message ?? "Value does not exist");
  }
}