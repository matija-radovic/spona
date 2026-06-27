import './HamburgerMenu.scss'
import { useEffect, useRef } from 'react'
import { useAnimate } from 'motion/react'
import { useTranslation } from 'react-i18next'

// Distance each outer line travels to meet center - must stay in sync with
// ($hamburger-height - $line-thickness) / 2 declared in HamburgerMenu.scss
const LINE_OFFSET = 9

const COLLAPSE = { duration: 0.08, ease: 'easeInOut' } as const
const FADE = { duration: 0.03 } as const
const SPRING = { type: 'spring', stiffness: 400, damping: 20 } as const

interface HamburgerMenuProps {
  isOpen: boolean
  onToggle: () => void
}

const HamburgerMenu = ({ isOpen, onToggle }: HamburgerMenuProps) => {
  const { t } = useTranslation()
  const isAnimating = useRef(false)
  const hasMounted = useRef(false)
  const [, animate] = useAnimate()

  const topRef = useRef<HTMLSpanElement>(null)
  const midRef = useRef<HTMLSpanElement>(null)
  const botRef = useRef<HTMLSpanElement>(null)

  // Morph the three lines to/from the X whenever the controlled `isOpen` flips.
  useEffect(() => {
    // Lines already render in their idle position, so skip the very first run.
    if (!hasMounted.current) {
      hasMounted.current = true
      return
    }

    const run = async () => {
      isAnimating.current = true

      if (isOpen) {
        await Promise.all([
          animate(topRef.current!, { y: LINE_OFFSET }, COLLAPSE),
          animate(botRef.current!, { y: -LINE_OFFSET }, COLLAPSE),
        ])
        await animate(midRef.current!, { opacity: 0 }, FADE)
        await Promise.all([
          animate(topRef.current!, { rotate: 45 }, SPRING),
          animate(botRef.current!, { rotate: -45 }, SPRING),
        ])
      } else {
        await Promise.all([
          animate(topRef.current!, { rotate: 0 }, COLLAPSE),
          animate(botRef.current!, { rotate: 0 }, COLLAPSE),
        ])
        await animate(midRef.current!, { opacity: 1 }, FADE)
        await Promise.all([
          animate(topRef.current!, { y: 0 }, SPRING),
          animate(botRef.current!, { y: 0 }, SPRING),
        ])
      }

      isAnimating.current = false
    }

    run()
  }, [isOpen, animate])

  const handleClick = () => {
    if (isAnimating.current) return
    onToggle()
  }

  return (
    <button
      className="hamburger"
      onClick={handleClick}
      aria-label={isOpen ? t('nav.closeMenu') : t('nav.openMenu')}
      aria-expanded={isOpen}
      aria-controls="mobile-nav-panel"
    >
      <span ref={topRef} className="hamburger-line" />
      <span ref={midRef} className="hamburger-line" />
      <span ref={botRef} className="hamburger-line" />
    </button>
  )
}

export default HamburgerMenu
