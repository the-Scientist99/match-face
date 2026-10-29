/** The largest centred square inside a `w`×`h` image. */
export function centerSquare(w: number, h: number): { sx: number; sy: number; side: number } {
  const side = Math.min(w, h)
  return { sx: Math.floor((w - side) / 2), sy: Math.floor((h - side) / 2), side }
}

/**
 * A photo as a centred square JPEG of at most `size` px (never upscaled). The browser does the decoding,
 * so HEIC and WebP work, the photo is turned upright from its EXIF orientation, and EXIF (GPS) is dropped.
 * The server re-checks and re-encodes it anyway. Rejects with `{ code: 'INVALID_IMAGE' }`.
 */
export async function toSquareJpeg(file: Blob, size = 512): Promise<Blob> {
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    throw { code: 'INVALID_IMAGE' }
  }
  const { sx, sy, side } = centerSquare(bitmap.width, bitmap.height)
  const out = Math.min(side, size)
  const canvas = document.createElement('canvas')
  canvas.width = out
  canvas.height = out
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = '#fff' // transparent PNGs get a white background, as on the server
  ctx.fillRect(0, 0, out, out)
  ctx.drawImage(bitmap, sx, sy, side, side, 0, 0, out, out)
  bitmap.close()
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject({ code: 'INVALID_IMAGE' })), 'image/jpeg', 0.9),
  )
}
