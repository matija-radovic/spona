import { useLayoutEffect, type RefObject } from "react";

const CSS_VAR = "--scrollbar-width";

/**
 * Measures the `ref`'s scrollbar gutter width and exposes it as a variable to its `ref`'s styles
 */
const useScrollbarGutter = <T extends HTMLElement>(ref: RefObject<T | null>) => {
    useLayoutEffect(() => {
        const el = ref.current;
        if (!el) return;
        const measure = () => {
            const bothEdges = getComputedStyle(el)
                .getPropertyValue("scrollbar-gutter")
                .includes("both-edges");

            const raw = el.offsetWidth - el.clientWidth;
            const width = bothEdges ? raw / 2 : raw;
            document.documentElement.style.setProperty(CSS_VAR, `${width}px`);
        };

        measure();

        const observer = new ResizeObserver(measure);
        observer.observe(el);
        return () => observer.disconnect();
    }, [ref]);
};

export default useScrollbarGutter;
