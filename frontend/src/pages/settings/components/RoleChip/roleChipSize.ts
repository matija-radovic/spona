export const RoleChipSize = Object.freeze({
  SM: 'sm',
  MD: 'md',
} as const)

export type RoleChipSize = (typeof RoleChipSize)[keyof typeof RoleChipSize]
