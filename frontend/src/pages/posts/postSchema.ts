import { z } from 'zod'
import { POST_MAX } from '@utils/constants/validation'
import type { CreatePostPayload } from '@utils/api/posts'

type Translate = (key: string, opts?: Record<string, unknown>) => string

export const buildPostSchema = (t: Translate) =>
  z.object({
    content: z
      .string()
      .max(POST_MAX, t('errors.maxLength', { max: POST_MAX }))
      .refine((value) => value.trim() !== '', t('errors.required')),
  })

export type PostFormData = z.infer<ReturnType<typeof buildPostSchema>>

export const POST_DEFAULTS: PostFormData = {
  content: '',
}

export const toCreatePostPayload = (
  values: PostFormData,
  createdForId: string,
): CreatePostPayload => ({
  content: values.content.trim(),
  createdForId,
})
