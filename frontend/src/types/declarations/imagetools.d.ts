// Types for vite-imagetools virtual module imports.
//
// IMPORTANT: do NOT `import ... from 'vite-imagetools'` (or 'imagetools-core')
// here. Those packages' type entrypoints `import` from 'vite' and 'sharp',
// both of which carry a `/// <reference types="node" />`. Pulling them into a
// `.d.ts` that's part of the app program force-loads @types/node globals,
// which clobbers the DOM `setTimeout` (returns `number`) with Node's
// (returns `NodeJS.Timeout`). We inline the shapes we actually use instead.

/** Mirror of vite-imagetools' `Picture` (imagetools-core). Self-contained. */
interface ImagetoolsPicture {
  /** Key is format, value is the srcset string. */
  sources: Record<string, string>;
  img: {
    src: string;
    w: number;
    h: number;
  };
}

/** The subset of vite-imagetools' `ImageMetadata` we consume at the app layer. */
interface ImagetoolsImageMetadata {
  src: string;
  width?: number;
  height?: number;
  format?: string;
  space?: string;
  channels?: number;
  [key: string]: unknown;
}

// predefined optimizations
declare module '*?defaultopt' {
  const out: ImagetoolsPicture;
  export default out;
}

// per-image optimizations

declare module '*&as=picture' {
  const out: ImagetoolsPicture;
  export default out;
}

declare module '*&as=metadata' {
  const out: ImagetoolsImageMetadata;
  export default out;
}

declare module '*&as=srcset' {
  const out: string;
  export default out;
}

// Catch-all for &imagetools-tagged imports - resolves to Picture
declare module '*&imagetools' {
  const out: ImagetoolsPicture;
  export default out;
}
