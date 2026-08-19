// -----------------------------------------------------------------------------
// MOCK upload service (backend contract §6); the real axios call is left commented
// above the mock body. Files are not database rows - the endpoint stores the object
// and answers with the URL, which is the only thing `avatarUrl` ever holds.
// -----------------------------------------------------------------------------

// import api from '@utils/api'

import { resizeImageToSquare } from '@utils/image/resizeImage'

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

/** Stands in for the object store; the real base URL comes back from the endpoint. */
const AVATAR_BASE_URL = 'https://cdn.spona.rs/avatars'

/**
 * MOCK - would be: const { data } = await api.post('/uploads/avatar', { image })
 *                  and then: return data.url
 * The 256x256 WebP is produced in the browser first, so the endpoint stores exactly
 * what the picker previewed. Rejects when the file is not a decodable image.
 */
export const uploadAvatar = async (file: File): Promise<string> => {
  const image = await resizeImageToSquare(file)
  void image // the mock keeps nothing; the real call sends this as the body
  await delay(600)
  return `${AVATAR_BASE_URL}/${crypto.randomUUID()}.webp`
}
