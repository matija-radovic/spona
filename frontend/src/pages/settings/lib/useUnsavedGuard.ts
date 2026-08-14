import { useCallback, useEffect, useState } from 'react'
import { useBlocker } from 'react-router-dom'

interface UnsavedGuardOptions {
  isDirty: boolean
  onSave: () => Promise<boolean>
  onDiscard: () => void
}

export interface UnsavedGuard {
  isOpen: boolean
  isSaving: boolean
  save: () => Promise<void>
  discard: () => void
  stay: () => void
}

const useUnsavedGuard = ({ isDirty, onSave, onDiscard }: UnsavedGuardOptions): UnsavedGuard => {
  const [isSaving, setIsSaving] = useState(false)

  const blocker = useBlocker(
    useCallback(
      ({ currentLocation, nextLocation }: { currentLocation: { pathname: string }; nextLocation: { pathname: string } }) =>
        isDirty && currentLocation.pathname !== nextLocation.pathname,
      [isDirty],
    ),
  )

  // A save elsewhere can clear the form mid-block; release it rather than strand the modal.
  useEffect(() => {
    if (blocker.state === 'blocked' && !isDirty) blocker.reset?.()
  }, [blocker, isDirty])

  const save = async () => {
    setIsSaving(true)
    try {
      const saved = await onSave()
      // Success cancels the navigation rather than resuming it; a failure keeps the modal up.
      if (saved) blocker.reset?.()
    } finally {
      setIsSaving(false)
    }
  }

  const discard = () => {
    onDiscard()
    blocker.proceed?.()
  }

  const stay = () => blocker.reset?.()

  return { isOpen: blocker.state === 'blocked', isSaving, save, discard, stay }
}

export default useUnsavedGuard
