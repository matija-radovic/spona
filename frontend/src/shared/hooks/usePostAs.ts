import { useCallback, useState } from 'react'
import { useUser } from '@shared/contexts'
import { getStoredPostAs, setStoredPostAs } from '@utils/postAs'
import type { MyOrganizationDto } from '@utils/api/memberships'
import type { PostAsValue } from '@shared/components/PostAsSelect/postAsValue'

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
const usePostAs = (publishable: readonly MyOrganizationDto[]): PostAs => {
  const { user } = useUser()
  const [stored, setStored] = useState<PostAsValue>(() => (user ? getStoredPostAs(user.id) : null))

  const value = publishable.some((row) => row.organization.id === stored) ? stored : null

  const setValue = useCallback(
    (next: PostAsValue) => {
      if (user) setStoredPostAs(user.id, next)
      setStored(next)
    },
    [user],
  )

  return { value, setValue, createdForId: value ?? user?.id ?? '' }
}

export default usePostAs
