import type { PostDto } from '@utils/api/posts'

const posts = new Map<string, PostDto>()
const gone = new Set<string>()

export const cachePost = (post: PostDto): void => {
  posts.set(post.id, post)
}

export const getCachedPost = (id: string): PostDto | undefined =>
  gone.has(id) ? undefined : posts.get(id)

export const forgetPost = (id: string): void => {
  posts.delete(id)
  gone.add(id)
}

export const isPostGone = (id: string): boolean => gone.has(id)

export const clearPostCache = (): void => {
  posts.clear()
  gone.clear()
}
