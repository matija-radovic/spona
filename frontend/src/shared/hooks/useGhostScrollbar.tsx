import { useEffect, type RefObject } from "react";

const SCROLL_HIDE_DELAY_MS = 1000;

interface GhostScrollbarOptions {
    /** Idle time (ms) after the last scroll before the thumb fades out. */
    hideDelay?: number;
}

/**
 * Reveals a ghost scrollbar *while the user is actively
 * scrolling*, then hides it again after a short idle delay.
 */
const useGhostScrollbar = <T extends HTMLElement>(
    ref: RefObject<T | null>,
    { hideDelay = SCROLL_HIDE_DELAY_MS }: GhostScrollbarOptions = {},
) => {
    useEffect(() => {
        const el = ref.current;
        if (!el) return;

        let timer: number | undefined;

        const handleScroll = () => {
            el.classList.add("is-scrolling");
            clearTimeout(timer);
            timer = window.setTimeout(
                () => el.classList.remove("is-scrolling"),
                hideDelay,
            );
        };

        el.addEventListener("scroll", handleScroll, { passive: true });
        return () => {
            el.removeEventListener("scroll", handleScroll);
            clearTimeout(timer);
        };
    }, [ref, hideDelay]);
};

export default useGhostScrollbar;
