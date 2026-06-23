// Note: the `.scroll-container` styles are loaded globally from index.scss,
//  so they apply to any element using the class or the hook.
// Don't re-import the SCSS here or it ships twice.
import { useRef, type CSSProperties, type ReactNode } from 'react'
import useGhostScrollbar from '@shared/hooks/useGhostScrollbar'

interface ScrollContainerProps {
    children: ReactNode,
    className?: string,
    style?: CSSProperties,
}

/**
 * Drop-in wrapper that gives its children a thin, ghost scrollbar.
 *
 * The scrollbar is invisible by default and:
 *   - appears on hover (pure CSS),
 *   - appears while actively scrolling, then fades,
 *   - never reserves layout-shifting space (scrollbar-gutter: stable),
 *   - is not shown at all when the content fits (overflow-y: auto).
 *
 * If you only need the hover behaviour, you don't need this component,
 * just add the `scroll-container` class to any element. Use this or the
 * `useGhostScrollbar` hook directly when you also want the active-scroll
 * reveal for keyboard/touch/programmatic scrolling.
 */
const ScrollContainer = ({
    children,
    className = '',
    style,
}: ScrollContainerProps) => {
    const ref = useRef<HTMLDivElement>(null)
    useGhostScrollbar(ref)

    const cls = ['scroll-container', className].filter(Boolean).join(' ')

    return (
        <div ref={ref} className={cls} style={style}>
            {children}
        </div>
    )
}

export default ScrollContainer
