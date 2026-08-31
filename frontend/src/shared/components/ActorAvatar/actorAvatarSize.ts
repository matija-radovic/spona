export const ActorAvatarSize = Object.freeze({
  XS: 'xs',
  SM: 'sm',
  MD: 'md',
  XL: 'xl',
} as const)

export type ActorAvatarSize = (typeof ActorAvatarSize)[keyof typeof ActorAvatarSize]
