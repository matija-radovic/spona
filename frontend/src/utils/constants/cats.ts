/** The cats the error pages draw from. Dropping a gif in the folder adds one. */
const CATS = Object.values(
  import.meta.glob<string>('../../assets/cats/*.gif', { eager: true, import: 'default' }),
)

export const getRandomCat = (): string => CATS[Math.floor(Math.random() * CATS.length)]
