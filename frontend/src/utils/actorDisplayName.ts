export const actorDisplayName = (name: string | null | undefined, handle: string): string =>
  name?.trim() || handle
