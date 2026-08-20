import './AuthCard.scss'
import type { ReactNode } from 'react'

interface AuthCardProps {
  title: string
  subtitle?: string
  children?: ReactNode
  footer?: ReactNode
}

/**
 * Card shell for auth screens (login, register, password reset).
 * Wrap action buttons in a `div className="auth-card-actions"` in
 * `children` (or pass a single button as `footer`) to opt into the
 * layout AuthCard.scss defines for that class: ghost button(s) pinned
 * left and everything else right-aligned, a lone button always
 * right-aligned regardless of variant, and every button stretching
 * full-width once the row wraps.
 */
const AuthCard = ({ title, subtitle, children, footer }: AuthCardProps) => (
  <div className="auth-card-wrap">
    <div className="auth-card">
      <header className="auth-card-head">
        <h2>{title}</h2>
        {subtitle && <p className="auth-card-sub">{subtitle}</p>}
      </header>

      {children && <div className="auth-card-body">{children}</div>}
      {footer && <div className="auth-card-footer">{footer}</div>}
    </div>
  </div>
)

export default AuthCard
