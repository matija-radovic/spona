import { useCallback, useEffect, useRef } from 'react'

const BASE_Z_INDEX = 110
const LAYER_STEP = 10
const Z_INDEX_VAR = '--modal-z-index'

const openLayers: symbol[] = []

/**
 * Registers a portalled overlay in a shared stack for as long as its node is
 * mounted, and hands back the ref to attach to that node:
 *
 * ```tsx
 * <div className="modal" ref={useModalLayer(onClose)}>
 * ```
 *
 * The stack is what keeps overlays sane once they nest: each one gets a z-index
 * above the header and above whatever opened before it, Esc only closes the
 * topmost, and the body scroll lock is lifted by the last one to close.
 */
const useModalLayer = (onClose: () => void) => {
  const onCloseRef = useRef(onClose)

  useEffect(() => {
    onCloseRef.current = onClose
  })

  return useCallback((node: HTMLElement | null) => {
    if (!node) return

    const layer = Symbol('modal-layer')
    openLayers.push(layer)

    node.style.setProperty(Z_INDEX_VAR, String(BASE_Z_INDEX + (openLayers.length - 1) * LAYER_STEP))
    if (openLayers.length === 1) document.body.style.overflow = 'hidden'

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && openLayers.at(-1) === layer) onCloseRef.current()
    }

    window.addEventListener('keydown', onKeyDown)

    return () => {
      window.removeEventListener('keydown', onKeyDown)
      openLayers.splice(openLayers.indexOf(layer), 1)
      if (openLayers.length === 0) document.body.style.overflow = ''
    }
  }, [])
}

export default useModalLayer
