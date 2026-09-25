import { useEffect, useLayoutEffect, useRef, type RefObject } from "react";
import { useLocation, useMatches, useNavigationType } from "react-router-dom";

export interface ScrollHandle {
    keepScroll?: boolean;
}

const positions = new Map<string, number>();

const useScrollReset = <T extends HTMLElement>(ref: RefObject<T | null>) => {
    const location = useLocation();
    const navigationType = useNavigationType();
    const matches = useMatches();
    const keyRef = useRef(location.key);

    const page = [...matches].reverse().find((m) => !(m.handle as ScrollHandle | undefined)?.keepScroll);
    const pageKey = page?.pathname ?? location.pathname;
    const prevPageKey = useRef(pageKey);

    useLayoutEffect(() => {
        keyRef.current = location.key;
    }, [location.key]);

    useEffect(() => {
        const el = ref.current;
        if (!el) return;
        const onScroll = () => positions.set(keyRef.current, el.scrollTop);
        el.addEventListener("scroll", onScroll, { passive: true });
        return () => el.removeEventListener("scroll", onScroll);
    }, [ref]);

    useLayoutEffect(() => {
        if (prevPageKey.current === pageKey) return;
        prevPageKey.current = pageKey;
        const el = ref.current;
        if (!el) return;
        if (navigationType === "PUSH") {
            el.scrollTop = 0;
        } else if (navigationType === "POP") {
            el.scrollTop = positions.get(location.key) ?? 0;
        }
    }, [ref, pageKey, navigationType, location.key]);
};

export default useScrollReset;
