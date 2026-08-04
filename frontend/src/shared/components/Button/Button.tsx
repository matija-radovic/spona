import './Button.scss'
import { Link } from 'react-router-dom'
import type { ButtonHTMLAttributes, ReactNode } from 'react'

export const ButtonVariant = Object.freeze({
  PRIMARY: 'primary',
  SECONDARY: 'secondary',
  TERTIARY: 'tertiary',
  QUATERNARY: 'quaternary',
  GHOST: 'ghost',
} as const)

export type ButtonVariant = (typeof ButtonVariant)[keyof typeof ButtonVariant]

export const ButtonSize = Object.freeze({
  SMALL: 'small',
  MEDIUM: 'medium',
  LARGE: 'large',
} as const)

export type ButtonSize = (typeof ButtonSize)[keyof typeof ButtonSize]

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
  /** When set, the button renders as a plain <a href> - for targets outside the SPA. */
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

  if (href) {
    return (
      <a
        href={href}
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
