import './DefaultLayout.scss'
import { useEffect, useRef } from "react"
import { Outlet } from "react-router-dom"
import { Header } from "@shared/components/Header"
import { Footer } from "@shared/components/Footer"
import { markInitialLoadDone, useTrackLastSection } from "@shared/hooks/useLastSection"
import useGhostScrollbar from '@shared/hooks/useGhostScrollbar'

const DefaultLayout = () => {
    const mainRef = useRef<HTMLElement>(null)
    useGhostScrollbar(mainRef)
    useTrackLastSection()

    useEffect(() => {
        markInitialLoadDone()
    }, [])

    return (
        <div className="dl">
            <Header />
            <main ref={mainRef} className="dl-main scroll-container">
                <div className="dl-content">
                    <Outlet />
                </div>
                <Footer />
            </main>
        </div>
    )
}

export default DefaultLayout
