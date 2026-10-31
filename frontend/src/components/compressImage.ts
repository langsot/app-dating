import { fitSize } from "../logic/photoSize";

const JPEG_QUALITY = 0.8;

async function decode(file: Blob): Promise<{ source: CanvasImageSource; width: number; height: number; close: () => void }> {
  if (typeof createImageBitmap === "function") {
    try {
      const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
      return { source: bitmap, width: bitmap.width, height: bitmap.height, close: () => bitmap.close() };
    } catch {
      // Старые WebView не поддерживают этот способ: ниже декодирование через <img>
    }
  }
  const url = URL.createObjectURL(file);
  const img = new Image();
  try {
    await new Promise<void>((done, fail) => {
      img.onload = () => done();
      img.onerror = () => fail(new Error("изображение не декодируется"));
      img.src = url;
    });
  } catch (e) {
    URL.revokeObjectURL(url);
    throw e;
  }
  return { source: img, width: img.naturalWidth, height: img.naturalHeight, close: () => URL.revokeObjectURL(url) };
}

/**
 * Уменьшает фотографию и перекодирует ее в JPEG. Перерисовка на canvas также убирает
 * метаданные EXIF, в том числе координаты съемки.
 */
export async function compressImage(file: Blob): Promise<Blob> {
  const image = await decode(file);
  try {
    const { width, height } = fitSize(image.width, image.height);
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("canvas недоступен");
    // У JPEG нет прозрачности: прозрачные области PNG становятся белыми
    ctx.fillStyle = "#fff";
    ctx.fillRect(0, 0, width, height);
    ctx.drawImage(image.source, 0, 0, width, height);
    const blob = await new Promise<Blob | null>((done) => canvas.toBlob(done, "image/jpeg", JPEG_QUALITY));
    if (!blob) throw new Error("не удалось получить JPEG");
    return blob;
  } finally {
    image.close();
  }
}
