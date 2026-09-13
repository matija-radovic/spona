import { AVATAR_SIZE } from '@utils/constants/validation'

/**
 * Resizes an image file to a `size`x`size` square entirely in the browser.
 * Uses `object-fit: cover` semantics: the source is scaled so the smaller side
 * fills the square (scaled up when smaller than the target) and whatever falls
 * outside the centered square is cropped off. Returns a PNG data URL.
 */
export const resizeImageToSquare = (file: File, size = AVATAR_SIZE): Promise<string> =>
  new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file)
    const img = new Image()

    img.onload = () => {
      const canvas = document.createElement('canvas')
      canvas.width = size
      canvas.height = size

      const ctx = canvas.getContext('2d')
      if (!ctx) {
        URL.revokeObjectURL(objectUrl)
        reject(new Error('Canvas 2D context unavailable.'))
        return
      }

      const scale = Math.max(size / img.width, size / img.height)
      const drawWidth = img.width * scale
      const drawHeight = img.height * scale
      const offsetX = (size - drawWidth) / 2
      const offsetY = (size - drawHeight) / 2

      ctx.drawImage(img, offsetX, offsetY, drawWidth, drawHeight)
      URL.revokeObjectURL(objectUrl)
      resolve(canvas.toDataURL('image/png'))
    }

    img.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new Error('Could not load the selected image.'))
    }

    img.src = objectUrl
  })
