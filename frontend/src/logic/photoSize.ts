/** Наибольшая сторона фотографии после сжатия, пикселей. */
export const MAX_PHOTO_SIDE = 1280;

/** Размеры изображения, вписанного в MAX_PHOTO_SIDE с сохранением пропорций. Мелкие снимки не увеличиваются. */
export function fitSize(width: number, height: number, maxSide: number = MAX_PHOTO_SIDE): { width: number; height: number } {
  const scale = Math.min(1, maxSide / Math.max(width, height));
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}
