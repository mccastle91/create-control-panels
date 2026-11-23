"""Draws the designer's window textures.

    python tools/gui_textures.py

Writes textures/gui/console/: the parts every window shares (frame, footer
panel, slot, text field, buttons, the angle slider), and a title bar and a
body tile for each theme in <theme>/. The colours here are the ones the
screens were drawn in before they had textures, so the mod's own look is
unchanged; a resource pack replaces any of these files to restyle it (see
docs/resource-packs.md for what each one is and how it is stretched).
Needs only Python's standard library.
"""
import os
import struct
import zlib

from plate_textures import read_png

HERE = os.path.dirname(os.path.abspath(__file__))
GUI = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'create_controlling', 'textures', 'gui')
OUT = os.path.join(GUI, 'console')

# theme: title bar highlight, fill, shadow; the lighter of the body's two checker tones
THEMES = {
    'andesite': (0xD9DBD2, 0xA9ABA2, 0x4A4C48, 0x575757),
    # Create's own brass menu colours (Sequenced Gearshift, Display Link)
    'brass': (0xFFEB8C, 0xE4B763, 0x592424, 0x442B15),
    'copper': (0xF7B890, 0xD9845A, 0x5A2A1C, 0x4A2A1E),
    # train casing: dark slate
    'railway': (0x8388A0, 0x565A6B, 0x1E1F27, 0x30323D),
    'industrial_iron': (0x9A9B9E, 0x6F7072, 0x2A2A2C, 0x3A3B3D),
    'weathered_iron': (0xB39A82, 0x8A7560, 0x3A2E24, 0x43372D),
    # the copycat console: zinc
    'copycat': (0xD5E3D8, 0xA9BDAF, 0x3F4D45, 0x4A5650),
    # the designer opened on a blueprint, in the blue of Create's schematics
    'blueprint': (0x9CC4F2, 0x5B8FD6, 0x1D3A6B, 0x2F4A78),
}

BLACK, WHITE = 0x000000, 0xFFFFFF
FRAME, FRAME_TOP = 0xB8B8B8, 0x787878
PANEL, PANEL_SHADOW = 0xC6C6C6, 0x555555
SLOT, SLOT_SHADOW = 0x8B8B8B, 0x373737


class Canvas:
    def __init__(self, width, height):
        self.width, self.height = width, height
        self.pixels = [[(0, 0, 0, 0)] * width for _ in range(height)]

    def fill(self, x0, y0, x1, y1, rgb):
        for y in range(max(0, y0), min(self.height, y1)):
            for x in range(max(0, x0), min(self.width, x1)):
                self.pixels[y][x] = ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255)

    def tile(self, x0, y0, x1, y1, tile, rgb):
        """The checker tile, tinted, as the screens used to draw it."""
        for y in range(y0, y1):
            for x in range(x0, x1):
                r, g, b = tile[(y - y0) % len(tile)][(x - x0) % len(tile[0])][:3]
                self.pixels[y][x] = (r * ((rgb >> 16) & 255) // 255, g * ((rgb >> 8) & 255) // 255,
                                     b * (rgb & 255) // 255, 255)

    def save(self, *path):
        target = os.path.join(OUT, *path)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        raw = b''.join(b'\x00' + b''.join(bytes(px) for px in row) for row in self.pixels)

        def chunk(kind, body):
            return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body) & 0xFFFFFFFF)

        with open(target, 'wb') as out:
            out.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', self.width, self.height, 8, 6, 0, 0, 0))
                      + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def button(name, highlight, fill, shadow):
    c = Canvas(16, 16)
    c.fill(0, 0, 16, 16, shadow)
    c.fill(0, 0, 15, 15, highlight)
    c.fill(1, 1, 15, 15, fill)
    c.save(name + '.png')


def main():
    checker = read_png(os.path.join(GUI, 'checker.png'))

    for theme, (highlight, fill, shadow, body) in THEMES.items():
        c = Canvas(16, 15)
        c.fill(0, 0, 16, 15, BLACK)
        c.fill(1, 1, 15, 14, shadow)
        c.fill(1, 1, 13, 13, highlight)
        c.fill(3, 2, 13, 13, fill)
        c.save(theme, 'title.png')
        c = Canvas(16, 16)
        c.tile(0, 0, 16, 16, checker, body)
        c.save(theme, 'body.png')

    # the body's frame: sides and top only, the footer closes it below
    c = Canvas(16, 16)
    c.fill(0, 0, 1, 16, BLACK)
    c.fill(15, 0, 16, 16, BLACK)
    c.fill(1, 0, 2, 16, FRAME)
    c.fill(14, 0, 15, 16, FRAME)
    c.fill(2, 0, 14, 1, FRAME_TOP)
    c.save('window_frame.png')

    # a small window with no bars, closed off all round
    c = Canvas(16, 16)
    c.fill(0, 0, 16, 16, BLACK)
    c.fill(1, 1, 15, 15, FRAME)
    for y in range(2, 14):
        for x in range(2, 14):
            c.pixels[y][x] = (0, 0, 0, 0)
    c.save('plaque.png')

    c = Canvas(16, 16)
    c.fill(0, 0, 16, 16, BLACK)
    c.fill(1, 1, 15, 15, PANEL_SHADOW)
    c.fill(1, 1, 13, 14, WHITE)
    c.fill(3, 2, 13, 14, PANEL)
    c.save('panel.png')

    c = Canvas(16, 16)
    c.fill(0, 0, 16, 16, WHITE)
    c.fill(0, 0, 15, 15, SLOT_SHADOW)
    c.fill(1, 1, 15, 15, SLOT)
    c.save('slot.png')

    c = Canvas(16, 14)
    c.fill(0, 0, 16, 14, SLOT)
    c.fill(0, 0, 16, 1, SLOT_SHADOW)
    c.fill(0, 13, 16, 14, WHITE)
    c.fill(0, 2, 16, 3, 0xA3A3A3)
    c.save('field.png')

    button('button', WHITE, SLOT, BLACK)
    button('button_hover', WHITE, 0x8892C9, 0x444964)
    button('button_green', 0xAEC57A, 0x218756, 0x0E2725)
    button('button_green_hover', 0xD4E8A0, 0x2A9A64, 0x0E2725)
    button('button_disabled', 0x8B8B8B, 0x6A6A6A, 0x373737)

    # Create's value bar: a dark track, and a brass handle carrying the value
    c = Canvas(16, 8)
    c.fill(0, 0, 16, 8, 0x0E0000)
    c.fill(1, 1, 15, 7, 0x392A25)
    c.fill(1, 2, 15, 6, 0x2B1A14)
    c.tile(1, 3, 15, 5, checker, 0x23120C)
    c.save('slider_track.png')
    for name, face in (('slider_handle', 0xFBDC7D), ('slider_handle_hover', 0xFFEB8C)):
        c = Canvas(16, 14)
        c.fill(0, 0, 16, 14, 0x704630)
        c.fill(1, 1, 15, 13, face)
        c.tile(1, 2, 15, 12, checker, 0xCEA05A)
        c.fill(1, 12, 15, 13, 0x9C6846)
        c.save(name + '.png')


if __name__ == '__main__':
    main()
