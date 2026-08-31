// Generated from _mediaqueries.scss - do not edit.
// Add or change a breakpoint in scss, then run `npm run gen:mediaqueries`.

export const MQ = {
  xsPhone: 'only screen and (max-width: 340px)',
  sPhone: 'only screen and (max-width: 380px)',
  mPhone: 'only screen and (max-width: 600px)',
  lPhone: 'only screen and (min-width: 601px) and (max-width: 768px)',
  phone: 'only screen and (max-width: 768px)',
  exceptPhone: 'only screen and (min-width: 769px)',
  tabletOnly: 'only screen and (min-width: 769px) and (max-width: 1024px)',
  tablet: 'only screen and (max-width: 1024px)',
  exceptTablet: 'only screen and (min-width: 1025px)',
  sTablet: 'only screen and (max-width: 860px)',
  xsDesktop: 'only screen and (max-width: 1200px)',
  sDesktop: 'only screen and (max-width: 1366px)',
  sDesktopOnly: 'only screen and (min-width: 1025px) and (max-width: 1200px)',
  desktop: 'only screen and (min-width: 1201px)',
  mDesktop: 'only screen and (min-width: 1201px) and (max-width: 1400px)',
  lDesktop: 'only screen and (min-width: 1401px)',
  xlDesktop: 'only screen and (max-width: 1500px)',
} as const

export type MediaQueryName = keyof typeof MQ
