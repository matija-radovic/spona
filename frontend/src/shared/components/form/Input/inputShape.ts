/** FIELD is the 10px form box; PILL is the 999px search well. */
export const InputShape = Object.freeze({ FIELD: 'field', PILL: 'pill' } as const)

export type InputShape = (typeof InputShape)[keyof typeof InputShape]
