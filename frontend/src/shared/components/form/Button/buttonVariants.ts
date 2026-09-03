export const ButtonVariant = Object.freeze({
  PRIMARY: 'primary',
  SECONDARY: 'secondary',
  TERTIARY: 'tertiary',
  QUATERNARY: 'quaternary',
  GHOST: 'ghost',
  DANGER: 'danger',
  DANGER_OUTLINE: 'danger-outline',
} as const)

export type ButtonVariant = (typeof ButtonVariant)[keyof typeof ButtonVariant]

export const ButtonSize = Object.freeze({
  SMALL: 'small',
  MEDIUM: 'medium',
  LARGE: 'large',
} as const)

export type ButtonSize = (typeof ButtonSize)[keyof typeof ButtonSize]
