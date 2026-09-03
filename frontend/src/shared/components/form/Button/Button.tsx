import './Button.scss'
import { Link } from 'react-router-dom'
import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { safeHref } from '@utils/safeUrl'
import { ButtonVariant, ButtonSize } from './buttonVariants'

const classesOf = (variant: ButtonVariant, size: ButtonSize, fullWidth: boolean, className?: string) =>
  ['btn', `btn-${variant}`, `btn-${size}`, fullWidth ? 'btn-block' : '', className ?? '']
    .filter(Boolean)
    .join(' ')

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  fullWidth?: boolean
  /** When set, the button renders as a react-router <Link> to this path. */
  to?: string
  /**
   * When set, the button renders as a plain <a href> - for targets outside the
   * SPA. Sanitized through `safeHref`; a URL that can't be made safe (a
   * `javascript:` scheme, junk from a backend field) falls back to a plain
   * <button> that does nothing, rather than shipping a live hostile link.
   */
  href?: string
  /** Only with `href`: opens in a new tab with a safe `rel`. */
  external?: boolean
  children: ReactNode
}

const Button = ({
  variant = ButtonVariant.PRIMARY,
  size = ButtonSize.MEDIUM,
  fullWidth = false,
  to,
  href,
  external = false,
  className,
  children,
  type = 'button',
  ...rest
}: ButtonProps) => {
  const classes = classesOf(variant, size, fullWidth, className)

  if (to) {
    return (
      <Link to={to} className={classes}>
        {children}
      </Link>
    )
  }

  const safe = safeHref(href)
  if (safe) {
    return (
      <a
        href={safe}
        className={classes}
        {...(external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
      >
        {children}
      </a>
    )
  }

  return (
    <button type={type} className={classes} {...rest}>
      {children}
    </button>
  )
}

export default Button
