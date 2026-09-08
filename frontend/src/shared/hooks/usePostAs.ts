import { useCallback, useState } from 'react'
import { useAuthenticatedUser } from '@shared/contexts'
import { getStoredPostAs, setStoredPostAs } from '@utils/postAs'
import type { MyOrganizationDto } from '@utils/api/memberships'
import type { PostAsValue } from '@shared/components/actor/PostAsSelect/postAsValue'

interface PostAs {
  /** `null` = as myself. Always one of `publishable`, or null. */
  value: PostAsValue
  setValue: (next: PostAsValue) => void
  /** The actor id every content write sends; never null on the wire. */
  createdForId: string
}

/**
 * Remembers which organization you last posted as, re-checks that it's still allowed on this
 * form every render, and hands the composer the actor id to send.
 */
const usePostAs = (publishable: readonly MyOrganizationDto[], requested?: PostAsValue): PostAs => {
  const user = useAuthenticatedUser('usePostAs')
  const [stored, setStored] = useState<PostAsValue>(() => requested ?? getStoredPostAs(user.id))

  const value = publishable.some((row) => row.organization.id === stored) ? stored : null

  const setValue = useCallback(
    (next: PostAsValue) => {
      setStoredPostAs(user.id, next)
      setStored(next)
    },
    [user.id],
  )

  return { value, setValue, createdForId: value ?? user.id }
}

export default usePostAs
