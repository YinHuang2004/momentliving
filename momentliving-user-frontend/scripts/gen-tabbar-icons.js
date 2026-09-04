/**
 * 生成 tabbar 占位图标（81x81 PNG，未选中 #7A8580 / 选中 #6B8E5A）+ logo.png
 * 纯 Node 实现（无第三方依赖）：像素绘制 + 手写 PNG 编码（zlib.deflateSync）
 * 用法：node scripts/gen-tabbar-icons.js
 */
const fs = require('fs')
const path = require('path')
const zlib = require('zlib')

const SIZE = 81
const OUT_DIR = path.join(__dirname, '..', 'static', 'tabbar')
const INACTIVE = [122, 133, 128, 255] // #7A8580
const ACTIVE = [107, 142, 90, 255]    // #6B8E5A

// ---------- PNG 编码 ----------
const CRC_TABLE = (() => {
  const table = new Int32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    table[n] = c
  }
  return table
})()

function crc32(buf) {
  let c = 0xffffffff
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([len, body, crc])
}

function encodePng(pixels, size) {
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(size, 0)
  ihdr.writeUInt32BE(size, 4)
  ihdr[8] = 8 // bit depth
  ihdr[9] = 6 // RGBA
  // 每行前加 filter byte 0
  const raw = Buffer.alloc(size * (size * 4 + 1))
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const [r, g, b, a] = pixels[y][x]
      const off = y * (size * 4 + 1) + 1 + x * 4
      raw[off] = r; raw[off + 1] = g; raw[off + 2] = b; raw[off + 3] = a
    }
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ])
}

// ---------- 画布与图元 ----------
function newCanvas() {
  return Array.from({ length: SIZE }, () => Array.from({ length: SIZE }, () => [0, 0, 0, 0]))
}

function draw(canvas, x, y, color) {
  x = Math.round(x); y = Math.round(y)
  // 按画布实际尺寸做越界检查（tabbar 81 / logo 240）
  if (x < 0 || y < 0 || y >= canvas.length || x >= canvas[y].length) return
  canvas[y][x] = color
}

const erase = [0, 0, 0, 0]

function fillRect(c, x1, y1, x2, y2, color, radius = 0) {
  for (let y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
    for (let x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
      if (radius > 0) {
        const rx1 = x1 + radius, rx2 = x2 - radius, ry1 = y1 + radius, ry2 = y2 - radius
        const inCorner =
          (x < rx1 && y < ry1 && dist(x, y, rx1, ry1) > radius) ||
          (x > rx2 && y < ry1 && dist(x, y, rx2, ry1) > radius) ||
          (x < rx1 && y > ry2 && dist(x, y, rx1, ry2) > radius) ||
          (x > rx2 && y > ry2 && dist(x, y, rx2, ry2) > radius)
        if (inCorner) continue
      }
      draw(c, x, y, color)
    }
  }
}

function fillCircle(c, cx, cy, r, color) {
  for (let y = Math.floor(cy - r); y <= cy + r; y++) {
    for (let x = Math.floor(cx - r); x <= cx + r; x++) {
      if (dist(x, y, cx, cy) <= r) draw(c, x, y, color)
    }
  }
}

function ring(c, cx, cy, rOuter, rInner, color) {
  for (let y = Math.floor(cy - rOuter); y <= cy + rOuter; y++) {
    for (let x = Math.floor(cx - rOuter); x <= cx + rOuter; x++) {
      const d = dist(x, y, cx, cy)
      if (d <= rOuter && d >= rInner) draw(c, x, y, color)
    }
  }
}

function fillPoly(c, pts, color) {
  let minY = Infinity, maxY = -Infinity
  for (const [, py] of pts) { minY = Math.min(minY, py); maxY = Math.max(maxY, py) }
  for (let y = Math.floor(minY); y <= Math.ceil(maxY); y++) {
    const xs = []
    for (let i = 0; i < pts.length; i++) {
      const [x1, y1] = pts[i]
      const [x2, y2] = pts[(i + 1) % pts.length]
      if (y1 <= y && y2 > y) xs.push(x1 + ((y - y1) / (y2 - y1)) * (x2 - x1))
      else if (y2 <= y && y1 > y) xs.push(x2 + ((y - y2) / (y1 - y2)) * (x1 - x2))
    }
    xs.sort((a, b) => a - b)
    for (let i = 0; i + 1 < xs.length; i += 2) {
      for (let x = Math.round(xs[i]); x <= Math.round(xs[i + 1]); x++) draw(c, x, y, color)
    }
  }
}

const dist = (x1, y1, x2, y2) => Math.sqrt((x1 - x2) ** 2 + (y1 - y2) ** 2)

// ---------- 五个图标（solid 几何风） ----------
function drawHome(color) {
  const c = newCanvas()
  fillPoly(c, [[40, 10], [8, 40], [72, 40]], color) // 屋顶
  fillRect(c, 16, 38, 64, 70, color, 6) // 房体
  fillRect(c, 34, 52, 46, 70, erase) // 门
  return c
}

function drawSeckill(color) {
  const c = newCanvas()
  fillPoly(c, [[48, 6], [16, 44], [36, 44], [30, 74], [66, 32], [42, 32]], color) // 闪电
  return c
}

function drawBlog(color) {
  const c = newCanvas()
  fillRect(c, 12, 10, 68, 70, color, 12) // 卡片
  fillRect(c, 20, 18, 60, 62, erase, 8) // 内部掏空
  fillRect(c, 24, 28, 56, 33, color) // 行 1
  fillRect(c, 24, 40, 56, 45, color) // 行 2
  fillRect(c, 24, 52, 44, 57, color) // 行 3（短）
  return c
}

function drawMessage(color) {
  const c = newCanvas()
  ring(c, 40, 34, 26, 19, color) // 气泡环
  fillPoly(c, [[28, 56], [26, 70], [44, 57]], color) // 尾巴
  fillCircle(c, 30, 34, 3, color)
  fillCircle(c, 40, 34, 3, color)
  fillCircle(c, 50, 34, 3, color)
  return c
}

function drawMine(color) {
  const c = newCanvas()
  fillCircle(c, 40, 26, 13, color) // 头
  fillCircle(c, 40, 76, 28, color) // 肩（大圆下半）
  fillRect(c, 0, 0, SIZE, 62, erase) // 裁掉肩上半
  return c
}

// ---------- logo ----------
function drawLogo() {
  const size = 240
  const c = Array.from({ length: size }, () => Array.from({ length: size }, () => [0, 0, 0, 0]))
  const g = [107, 142, 90, 255]
  const w = [250, 247, 240, 255]
  // 品牌绿圆角方块
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const r = 56
      const rx1 = r, rx2 = size - r, ry1 = r, ry2 = size - r
      const inCorner =
        (x < rx1 && y < ry1 && dist(x, y, rx1, ry1) > r) ||
        (x > rx2 && y < ry1 && dist(x, y, rx2, ry1) > r) ||
        (x < rx1 && y > ry2 && dist(x, y, rx1, ry2) > r) ||
        (x > rx2 && y > ry2 && dist(x, y, rx2, ry2) > r)
      if (!inCorner) c[y][x] = g
    }
  }
  // 内部：白色圆环 + 指针 + 圆点（抽象"一刻"时钟）
  const cx = 120, cy = 120
  ring(c, cx, cy, 62, 48, w)
  fillRect(c, cx - 4, cy - 40, cx + 4, cy, w) // 指针（向上）
  fillRect(c, cx - 4, cy - 4, cx + 28, cy + 4, w) // 指针（向右）
  fillCircle(c, cx, cy, 10, w)
  return { png: encodePng(c, size), file: 'logo.png' }
}

// ---------- 输出 ----------
const icons = [
  ['home', drawHome],
  ['seckill', drawSeckill],
  ['blog', drawBlog],
  ['message', drawMessage],
  ['mine', drawMine]
]

fs.mkdirSync(OUT_DIR, { recursive: true })
const staticDir = path.join(__dirname, '..', 'static')

for (const [name, fn] of icons) {
  fs.writeFileSync(path.join(OUT_DIR, `${name}.png`), encodePng(fn(INACTIVE), SIZE))
  fs.writeFileSync(path.join(OUT_DIR, `${name}-active.png`), encodePng(fn(ACTIVE), SIZE))
  console.log(`✓ ${name}.png / ${name}-active.png`)
}

fs.writeFileSync(path.join(staticDir, 'logo.png'), drawLogo().png)
console.log('✓ logo.png')
