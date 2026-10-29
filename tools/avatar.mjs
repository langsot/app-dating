// Нейтральная заглушка вместо фотографии: PNG с цветным градиентом и силуэтом, без сторонних библиотек.
import { deflateSync } from "node:zlib";

const COLORS = [[94, 129, 172], [163, 190, 140], [208, 135, 112], [180, 142, 173], [136, 192, 208], [235, 203, 139]];
const W = 480;
const H = 640;

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});

function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

/** Силуэт: голова — круг, плечи — верхняя половина эллипса. */
function inSilhouette(x, y) {
  const head = (x - 240) ** 2 + (y - 260) ** 2 <= 95 ** 2;
  const body = y >= 400 && ((x - 240) / 190) ** 2 + ((y - 560) / 160) ** 2 <= 1;
  return head || body;
}

export function avatar(idx, variant = 0) {
  const c = COLORS[(idx + variant) % COLORS.length];
  const raw = Buffer.alloc((W * 3 + 1) * H);
  let p = 0;
  for (let y = 0; y < H; y++) {
    raw[p++] = 0; // фильтр строки: None
    const shade = 1 - 0.35 * (y / H);
    for (let x = 0; x < W; x++) {
      const white = inSilhouette(x, y);
      for (const v of c) raw[p++] = white ? Math.round(v * 0.25 + 255 * 0.75) : Math.round(v * shade);
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0);
  ihdr.writeUInt32BE(H, 4);
  ihdr[8] = 8; // 8 бит на канал
  ihdr[9] = 2; // RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}
