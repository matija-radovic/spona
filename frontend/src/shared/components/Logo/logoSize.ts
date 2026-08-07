export const LogoSize = Object.freeze({
  TINY: 'tiny',
  SMALL: 'small',
  MEDIUM: 'medium',
  LARGE: 'large',
} as const)

export type LogoSize = (typeof LogoSize)[keyof typeof LogoSize]
