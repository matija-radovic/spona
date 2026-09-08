export const PostCardVariant = Object.freeze({ BOXED: 'boxed', PLAIN: 'plain' } as const)
export type PostCardVariant = (typeof PostCardVariant)[keyof typeof PostCardVariant]
