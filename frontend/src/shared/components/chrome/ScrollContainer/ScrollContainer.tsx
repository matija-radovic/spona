import { useRef, type CSSProperties, type ReactNode } from 'react'
import useGhostScrollbar from '@shared/hooks/useGhostScrollbar'

interface ScrollContainerProps {
    children: ReactNode,
    className?: string,
    style?: CSSProperties,
}

/** Drop-in wrapper that gives its children a thin, ghost scrollbar. */
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
