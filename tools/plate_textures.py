"""Draws the dyed face plates from the standard one.

    python tools/plate_textures.py

Reads textures/block/console_plate.png and writes console_plate_<dye>.png
beside it for every dye but gray (gray dye gives the standard plate). Each
keeps the plate's own shading, light and dark, around the dye's colour. To
change how the dyed plates look, change STRENGTH or COLORS and run it again.
Needs only Python's standard library.
"""
import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
BLOCK = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'create_controlling', 'textures', 'block')

# Minecraft's dye colours (DyeColor's texture diffuse colours)
COLORS = {
    'white': 0xF9FFFE, 'orange': 0xF9801D, 'magenta': 0xC74EBD, 'light_blue': 0x3AB3DA,
    'yellow': 0xFED83D, 'lime': 0x80C71F, 'pink': 0xF38BAA, 'light_gray': 0x9D9D97,
    'cyan': 0x169C9C, 'purple': 0x8932B8, 'blue': 0x3C44AA, 'brown': 0x835432,
    'green': 0x5E7C16, 'red': 0xB02E26, 'black': 0x1D1D21,
}
# How bright the plate's average pixel comes out, as a share of the dye's colour:
# painted metal, a little duller than the dye itself
STRENGTH = 0.88


def read_png(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a PNG'
    pos, chunks = 8, []
    while pos < len(data):
        length, kind = struct.unpack('>I4s', data[pos:pos + 8])
        chunks.append((kind, data[pos + 8:pos + 8 + length]))
        pos += 12 + length
    width, height, depth, ctype, _, _, interlace = struct.unpack('>IIBBBBB', chunks[0][1])
    assert depth == 8 and interlace == 0, 'unsupported PNG: depth %d, interlace %d' % (depth, interlace)
    palette = next((body for kind, body in chunks if kind == b'PLTE'), None)
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(b''.join(body for kind, body in chunks if kind == b'IDAT'))
    stride = width * channels
    rows, previous, at = [], bytearray(stride), 0
    for _ in range(height):
        kind = raw[at]
        line = bytearray(raw[at + 1:at + 1 + stride])
        at += 1 + stride
        for i in range(stride):
            left = line[i - channels] if i >= channels else 0
            up = previous[i]
            corner = previous[i - channels] if i >= channels else 0
            if kind == 1:
                line[i] = (line[i] + left) & 255
            elif kind == 2:
                line[i] = (line[i] + up) & 255
            elif kind == 3:
                line[i] = (line[i] + (left + up) // 2) & 255
            elif kind == 4:
                p = left + up - corner
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - corner)
                line[i] = (line[i] + (left if pa <= pb and pa <= pc else up if pb <= pc else corner)) & 255
        rows.append(line)
        previous = line
    pixels = []
    for line in rows:
        row = []
        for x in range(width):
            px = line[x * channels:(x + 1) * channels]
            if ctype == 0:
                row.append((px[0], px[0], px[0]))
            elif ctype == 2 or ctype == 6:
                row.append((px[0], px[1], px[2]))
            elif ctype == 3:
                row.append(tuple(palette[px[0] * 3:px[0] * 3 + 3]))
            else:
                row.append((px[0], px[0], px[0]))
        pixels.append(row)
    return pixels


def write_png(path, pixels):
    raw = b''.join(b'\x00' + b''.join(bytes(px) for px in row) for row in pixels)

    def chunk(kind, body):
        return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body) & 0xFFFFFFFF)

    with open(path, 'wb') as out:
        out.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', len(pixels[0]), len(pixels), 8, 2, 0, 0, 0))
                  + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def main():
    plate = read_png(os.path.join(BLOCK, 'console_plate.png'))
    light = [[0.299 * r + 0.587 * g + 0.114 * b for r, g, b in row] for row in plate]
    mean = sum(sum(row) for row in light) / sum(len(row) for row in light)
    for name, rgb in COLORS.items():
        dye = ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255)
        # dark dyes get a floor, so the plate's shading still shows on them
        dye = tuple(max(c, 38) for c in dye)
        out = [[tuple(min(255, int(round(c * STRENGTH * value / mean))) for c in dye) for value in row] for row in light]
        write_png(os.path.join(BLOCK, 'console_plate_%s.png' % name), out)
    print('wrote %d plates (mean plate brightness %.0f)' % (len(COLORS), mean))


if __name__ == '__main__':
    main()
