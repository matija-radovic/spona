export const ActorRowVariant = Object.freeze({ BOXED: 'boxed', PLAIN: 'plain' } as const)
export type ActorRowVariant = (typeof ActorRowVariant)[keyof typeof ActorRowVariant]
