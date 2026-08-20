/** Pins/Aligns to the selected side */
export const TooltipAlign = Object.freeze({
  LEFT: 'left',
  RIGHT: 'right',
} as const)

export type TooltipAlign = (typeof TooltipAlign)[keyof typeof TooltipAlign]
