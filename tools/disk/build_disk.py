#!/usr/bin/env python3
"""Builds the mod's own duel disk, tools/disk/duel_disk.bbmodel, from code.

An original model in the style of the Battle City disk: a round hub on the outside of the left forearm with a red
centre and a red arm that reaches out to the blade, an LP counter, a deck holder and a graveyard tray, a padded
cuff, and a two-part blade with light-blue card zones (3 + 2) and red markers. Everything is shaped here (Blockbench
meshes, so the hub is round and the blade has a bevelled edge) and painted here (one texture atlas plus its _e glow
layer), so nothing comes from someone else's model.

Rest pose is standby, like a TIE fighter: the 3-zone wing in front of the hub, the 2-zone wing behind it, both along
the arm. `deploy` slides the 3-zone wing along the arm and swings the 2-zone wing over the hub onto its end, which
gives the bent 5-zone blade; `fold` plays it backwards.

Coordinates are Figura's (pixels, y up): the left arm is the box x 4..8, y 12..24, z -2..2, the hand at y 12, the
player faces -z. The disk's face looks out from the arm (+x).

    python3 tools/disk/build_disk.py
    python3 tools/disk/convert_disk.py
    python3 tools/textures/make_cosmetic_textures.py
"""
import argparse
import base64
import io
import json
import math
import uuid
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent

D = 4  # texels per model pixel
ATLAS = (256, 128)

# Colours (RGB). The skins in make_cosmetic_textures.py recolour every pale grey (saturation < 0.15, value > 0.45).
GREY = (214, 215, 210)
GREY_LIGHT = (234, 235, 231)
GREY_MID = (192, 193, 188)
GREY_DARK = (160, 161, 157)
TRIM = (62, 64, 72)
CUFF = (58, 60, 68)
CUFF_LIGHT = (84, 87, 97)
RED = (204, 34, 40)
RED_DARK = (150, 22, 30)
RED_LIGHT = (232, 78, 78)
ZONE = (98, 178, 232)
ZONE_EDGE = (62, 132, 196)
ZONE_GLOW = (20, 48, 68)
MARK = (212, 30, 38)
MARK_GLOW = (120, 12, 16)
WINDOW = (124, 196, 236)
WINDOW_GLOW = (18, 40, 56)
INDICATOR = (36, 66, 196)
INDICATOR_GLOW = (18, 36, 120)
DISPLAY = (24, 24, 30)
DIGIT = (255, 96, 48)
DIGIT_GLOW = (190, 60, 24)
LAMP = (246, 238, 196)
LAMP_GLOW = (120, 112, 72)
CARD_BROWN = (122, 64, 28)
CARD_DARK = (82, 40, 16)
CARD_EDGE = (226, 216, 192)
VENT = (36, 38, 46)
VENT_BLUE = (52, 118, 186)

# Layout
X0 = 8.3  # underside of the hub, on the cuff
HUB = (15.0, 0.0)  # hub centre (y, z)
HUB_R = 4.6
HUB_TOP = X0 + 1.2
RED_TOP = HUB_TOP + 0.8
BAR_TOP = RED_TOP + 0.7

BLADE_TOP = HUB_TOP  # top of the blade plate
BLADE_BOTTOM = BLADE_TOP - 0.9
BLADE_INNER = -6.4  # z of the blade's inner edge in front of the arm
BLADE_W = 6.4  # across the blade
BEVEL = 1.0  # how far the bevelled outer edge reaches out at the bottom
ZONE_LEN, ZONE_W, PITCH = 3.6, 4.8, 4.2  # card zone along and across the blade, zone spacing
ZONE_START, ZONE_T = 0.6, 0.8  # first zone from the joint, zones from the inner edge
TIP = 3.2  # the inner edge reaches this much further than the outer edge at the far end
JOINT_Y = 14.0  # where the two wings meet in the duel pose (inner corner)
BEND = 22.0  # degrees between the wings in the duel pose
LIFT = 3.8  # how high the 2-zone wing lifts while it swings over the hub
SCALE = 0.8  # the disk (not the cuff) is drawn at this size, about the hub's underside centre

LP_TEXT = "8000"
FONT = {  # 3x5 digits
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"], "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "111"],
}


def uid():
    return str(uuid.uuid4())


def r4(v):
    return round(float(v), 4)


# ---------------------------------------------------------------- texture atlas

class Atlas:
    """Shelf packer; each region is painted by a shader (part-local point -> base colour, glow colour)."""

    def __init__(self):
        self.base = Image.new("RGBA", ATLAS, (0, 0, 0, 0))
        self.glow = Image.new("RGBA", ATLAS, (0, 0, 0, 0))
        self.x = self.y = self.row = 1
        self.swatches = {}

    def alloc(self, w, h):
        w, h = int(math.ceil(w)), int(math.ceil(h))
        if self.x + w + 1 > ATLAS[0]:
            self.x, self.y, self.row = 1, self.y + self.row + 1, 0
        if self.y + h + 1 > ATLAS[1]:
            raise SystemExit("texture atlas is full")
        at = (self.x, self.y)
        self.x += w + 1
        self.row = max(self.row, h)
        return at

    def canvas(self, a0, a1, b0, b1, shader, flip_b=False):
        """A painted region for part-local coordinates (a, b) in [a0, a1] x [b0, b1]; returns point -> uv."""
        w, h = (a1 - a0) * D, (b1 - b0) * D
        u0, v0 = self.alloc(w + 2, h + 2)
        u0, v0 = u0 + 1, v0 + 1  # one texel of bleed on every side
        base, glow = self.base.load(), self.glow.load()
        for j in range(-1, int(math.ceil(h)) + 1):
            for i in range(-1, int(math.ceil(w)) + 1):
                ci, cj = min(max(i, 0), math.ceil(w) - 1), min(max(j, 0), math.ceil(h) - 1)
                a = a0 + (ci + 0.5) / D
                b = (b1 - (cj + 0.5) / D) if flip_b else (b0 + (cj + 0.5) / D)
                colour, light = shader(a, b)
                base[u0 + i, v0 + j] = tuple(colour) + (255,)
                if light:
                    glow[u0 + i, v0 + j] = tuple(light) + (255,)

        def uv(a, b):
            bb = (b1 - b) if flip_b else (b - b0)
            return [r4(u0 + (a - a0) * D), r4(v0 + bb * D)]
        return uv

    def swatch(self, colour):
        """A plain 4x4 colour patch for side faces; returns its four corner uvs."""
        if colour not in self.swatches:
            u, v = self.alloc(4, 4)
            px = self.base.load()
            for j in range(4):
                for i in range(4):
                    px[u + i, v + j] = tuple(colour) + (255,)
            self.swatches[colour] = [[u + 1, v + 1], [u + 3, v + 1], [u + 3, v + 3], [u + 1, v + 3]]
        return self.swatches[colour]


# ---------------------------------------------------------------- geometry

class Mesh:
    """A Blockbench mesh element. Faces are given in arm space; vertices are stored relative to the origin."""

    def __init__(self, name):
        self.name = name
        self.verts = []  # arm-space points
        self.faces = []  # (vertex indices, uvs)

    def face(self, points, uvs):
        idx = []
        for p in points:
            for k, q in enumerate(self.verts):
                if all(abs(p[i] - q[i]) < 1e-6 for i in range(3)):
                    idx.append(k)
                    break
            else:
                self.verts.append(tuple(float(c) for c in p))
                idx.append(len(self.verts) - 1)
        self.faces.append((idx, uvs))

    def element(self):
        lo = [min(v[i] for v in self.verts) for i in range(3)]
        hi = [max(v[i] for v in self.verts) for i in range(3)]
        origin = [r4((lo[i] + hi[i]) / 2) for i in range(3)]
        keys = [f"v{k}" for k in range(len(self.verts))]
        vertices = {keys[k]: [r4(v[i] - origin[i]) for i in range(3)] for k, v in enumerate(self.verts)}
        faces = {}
        for n, (idx, uvs) in enumerate(self.faces):
            faces[f"f{n}"] = {"uv": {keys[k]: uv for k, uv in zip(idx, uvs)}, "vertices": [keys[k] for k in idx],
                              "texture": 0}
        return {"name": self.name, "color": 0, "origin": origin, "rotation": [0, 0, 0], "export": True,
                "visibility": True, "locked": False, "render_order": "default", "allow_mirror_modeling": True,
                "vertices": vertices, "faces": faces, "type": "mesh", "uuid": uid()}


def fan(points):
    """Split a convex polygon into faces of at most four corners (index lists)."""
    n, out, k = len(points), [], 1
    while k + 2 <= n - 1:
        out.append([0, k, k + 1, k + 2])
        k += 2
    if k + 1 <= n - 1:
        out.append([0, k, k + 1])
    return out


def solid(mesh, top, bottom, top_uv, bottom_uv, side_colour, atlas, side_uv=None):
    """A slab between two outlines with the same corner count (convex, in order): top, bottom and side faces.
    top_uv / bottom_uv map an arm-space point to a uv; side_uv(edge index) may give a uv function for a side."""
    n = len(top)
    for f in fan(list(range(n))):
        mesh.face([top[i] for i in f], [top_uv(top[i]) for i in f])
        mesh.face([bottom[i] for i in f], [bottom_uv(bottom[i]) for i in f])
    sw = atlas.swatch(side_colour)
    for i in range(n):
        j = (i + 1) % n
        quad = [top[i], top[j], bottom[j], bottom[i]]
        fn = side_uv(i) if side_uv else None
        mesh.face(quad, [fn(p) for p in quad] if fn else sw)


def ring(centre, radius, count, start=0.0):
    return [(centre[0] + radius * math.cos(math.radians(start + 360 * k / count)),
             centre[1] + radius * math.sin(math.radians(start + 360 * k / count))) for k in range(count)]


def at_x(points2d, x):
    """(y, z) outline at height x -> arm-space points."""
    return [(x, y, z) for y, z in points2d]


def plain(atlas, colour):
    sw = atlas.swatch(colour)
    centre = [(sw[0][0] + sw[2][0]) / 2, (sw[0][1] + sw[2][1]) / 2]
    return lambda p: centre


def box(name, atlas, lo, hi, colour, top=None):
    """An axis-aligned box as a mesh; `top` is an optional uv function for its +x face."""
    m = Mesh(name)
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    outline = [(y0, z0), (y1, z0), (y1, z1), (y0, z1)]
    solid(m, at_x(outline, x1), at_x(outline, x0), top or plain(atlas, colour), plain(atlas, colour), colour, atlas)
    return m


# ---------------------------------------------------------------- shaders

def polar(y, z):
    dy, dz = y - HUB[0], z - HUB[1]
    return math.hypot(dy, dz), math.degrees(math.atan2(dz, dy)) % 360


def hub_top(y, z):
    r, a = polar(y, z)
    if r > HUB_R - 0.25:
        return GREY_DARK, None
    if r > HUB_R - 0.6:
        return GREY_MID, None
    if 3.75 < r < 3.95:
        return GREY_DARK, None
    if 2.3 < r < 3.5 and 25 < a < 155:
        # three windows round the back of the hub
        for lo, hi in ((25, 67), (69, 111), (113, 155)):
            if lo + 1 < a < hi - 1:
                edge = r < 2.45 or r > 3.35 or a < lo + 3 or a > hi - 3
                return (ZONE_EDGE if edge else WINDOW), WINDOW_GLOW
        return GREY_MID, None
    if 2.15 < r < 2.3:
        return GREY_MID, None
    return GREY, None


def hub_bottom(y, z):
    return GREY_DARK, None


def red_disc(y, z):
    r = math.hypot(y - HUB[0], z - HUB[1])
    if r > 1.95:
        return RED_DARK, None
    if 1.25 < r < 1.45:
        return RED_DARK, None
    if r < 0.6:
        return RED_LIGHT, None
    return RED, None


def red_arm(y, z):
    # a little lighter towards the hub, darker at the prongs
    if abs(y - HUB[0]) > 1.85:
        return RED_DARK, None
    return (RED if z < -2.0 else RED_LIGHT), None


def indicator(y, z):
    # light grey plate with a dark blue triangle pointing at the blade (-z)
    zt, zb = -2.5, -4.3
    if zb < z < zt:
        half = 0.7 * (z - zb) / (zt - zb)
        if abs(y - HUB[0]) < half:
            return INDICATOR, INDICATOR_GLOW
    return GREY_LIGHT, None


BAR = (13.6, 20.0, -1.2, 1.2)
DISPLAY_AT = (15.6, 19.6, -0.8, 0.8)


def lp_bar(y, z):
    y0, y1, z0, z1 = DISPLAY_AT
    if y0 < y < y1 and z0 < z < z1:
        # digits read along the bar from the elbow towards the hand, upright with +z up (as seen in game, where
        # Blockbench space shows mirrored)
        col = int((y1 - y) * D) - 1
        row = int((z1 - z) * D) - 1
        if 0 <= row < 5 and col >= 0:
            digit, x = divmod(col, 4)
            if digit < len(LP_TEXT) and x < 3 and FONT[LP_TEXT[digit]][row][x] == "1":
                return DIGIT, DIGIT_GLOW
        return DISPLAY, None
    # red wedge on the hand side
    if 13.9 < y < 15.3 and abs(z) < 0.25 + 0.45 * (y - 13.9) / 1.4:
        return RED, None
    if abs(z) > 1.0:
        return GREY_MID, None
    return GREY_LIGHT, None


def card_back(y, z):
    dy, dz = (y - 11.4) / 1.7, z / 1.1
    if abs(z) > 1.15 or abs(y - 11.4) > 1.8:
        return CARD_BROWN, None
    e = dy * dy + dz * dz
    if e < 0.28:
        return (14, 10, 8), None
    if e < 0.42:
        return CARD_DARK, None
    if e < 0.5:
        return (176, 118, 48), None
    return CARD_BROWN, None


def tray_top(y, z):
    if 19.8 < y < 22.8 and abs(z) < 1.2:
        return TRIM, None
    return GREY, None


def blade_top(count, s_end_outer):
    zones = [ZONE_START + k * PITCH for k in range(count)]

    def inside(s, t):
        for s0 in zones:
            if s0 < s < s0 + ZONE_LEN and ZONE_T < t < ZONE_T + ZONE_W:
                edge = min(s - s0, s0 + ZONE_LEN - s)
                depth = ZONE_T + ZONE_W - t
                if depth < 1.3 and edge < 0.45 or depth < 0.65 and edge < 0.9:
                    return None  # stepped notches at the outer corners
                return s0
        return None

    def shader(s, t):
        s0 = inside(s, t)
        if s0 is not None:
            mid = s0 + ZONE_LEN / 2
            if 1.2 < t - ZONE_T < 2.9 and abs(s - mid) < 1.15 * (1 - (t - ZONE_T - 1.2) / 1.7):
                return MARK, MARK_GLOW  # red triangle pointing to the outer edge
            step = 1 / D
            if any(inside(s + ds, t + dt) is None for ds, dt in ((step, 0), (-step, 0), (0, step), (0, -step))):
                return ZONE_EDGE, ZONE_GLOW
            return ZONE, ZONE_GLOW
        if t < 0.25:
            return GREY_LIGHT, None
        if t > BLADE_W - 0.25:
            return GREY_MID, None
        if 0.45 < t < BLADE_W - 0.45 and (s < 0.25 or s > s_end_outer - 0.6 + (BLADE_W - t) * TIP / BLADE_W):
            return GREY_MID, None  # seam at the joint and at the tip
        return GREY, None
    return shader


def bevel_shader(count):
    zones = [ZONE_START + k * PITCH + ZONE_LEN / 2 for k in range(count)]

    def shader(s, d):  # d: 0 at the top edge .. 1.41 at the bottom
        for mid in zones:
            if abs(s - mid) < 1.2 and 0.35 < d < 0.95:
                return (VENT_BLUE if d > 0.75 else VENT), (ZONE_GLOW if d > 0.75 else None)
        if d < 0.15:
            return GREY_LIGHT, None
        return GREY_MID, None
    return shader


# ---------------------------------------------------------------- the blade

class Wing:
    """One part of the blade, in its own (s, t) frame: s along the wing from the joint, t across from the inner
    edge. `place(s, t)` gives (y, z) in the rest (standby) pose."""

    def __init__(self, name, count, origin, s_dir, t_dir):
        self.name, self.count = name, count
        self.origin, self.s_dir, self.t_dir = origin, s_dir, t_dir
        self.miter = math.tan(math.radians(BEND / 2))
        self.outer_end = ZONE_START + (count - 1) * PITCH + ZONE_LEN + 0.8
        self.inner_end = self.outer_end + TIP

    def place(self, s, t):
        return (self.origin[0] + s * self.s_dir[0] + t * self.t_dir[0],
                self.origin[1] + s * self.s_dir[1] + t * self.t_dir[1])

    def local(self, y, z):
        dy, dz = y - self.origin[0], z - self.origin[1]
        return (dy * self.s_dir[0] + dz * self.s_dir[1], dy * self.t_dir[0] + dz * self.t_dir[1])

    def meshes(self, atlas):
        w, m = BLADE_W, self.miter
        top_st = [(0, 0), (self.inner_end, 0), (self.outer_end, w), (-w * m, w)]
        wb = w + BEVEL
        tip_bottom = self.inner_end - (self.inner_end - self.outer_end) * wb / w
        bottom_st = [(0, 0), (self.inner_end, 0), (tip_bottom, wb), (-wb * m, wb)]
        top = at_x([self.place(*p) for p in top_st], BLADE_TOP)
        bottom = at_x([self.place(*p) for p in bottom_st], BLADE_BOTTOM)

        s_lo, s_hi = -wb * m - 0.2, self.inner_end + 0.2
        top_uv = atlas.canvas(s_lo, s_hi, 0, w, blade_top(self.count, self.outer_end))
        under = plain(atlas, GREY_DARK)
        slant = math.hypot(BEVEL, BLADE_TOP - BLADE_BOTTOM)
        bevel_uv = atlas.canvas(s_lo, s_hi, 0, slant, bevel_shader(self.count))

        def side_uv(i):
            if i != 2:
                return None

            def fn(p):  # the bevelled outer edge: u along the wing, v down the slope
                s, t = self.local(p[1], p[2])
                return bevel_uv(s, (t - w) / BEVEL * slant if p[0] < BLADE_TOP - 1e-6 else 0)
            return fn

        plate = Mesh(f"{self.name.lower()}_plate")
        solid(plate, top, bottom, lambda p: top_uv(*self.local(p[1], p[2])), under, GREY, atlas, side_uv)

        # raised rail along the inner edge
        rail = Mesh(f"{self.name.lower()}_rail")
        rail_st = [(0.05, 0), (self.inner_end - 0.4, 0), (self.inner_end - 0.4 - 0.45 * TIP / w, 0.45),
                   (0.05 - 0.45 * m, 0.45)]
        outline = [self.place(*p) for p in rail_st]
        light = plain(atlas, GREY_LIGHT)
        solid(rail, at_x(outline, BLADE_TOP + 0.35), at_x(outline, BLADE_TOP), light, light, GREY_MID, atlas)
        return [plate, rail]


# ---------------------------------------------------------------- the model

def build():
    atlas = Atlas()
    groups = {"Bracer": [], "Hub": [], "WingThree": [], "WingTwo": []}

    # Cuff round the forearm with two lighter straps
    groups["Bracer"].append(box("cuff", atlas, (3.65, 13.0, -2.35), (8.35, 17.4, 2.35), CUFF))
    for name, y in (("strap_wrist", 13.0), ("strap_elbow", 16.9)):
        groups["Bracer"].append(box(name, atlas, (3.6, y, -2.4), (8.4, y + 0.5, 2.4), CUFF_LIGHT))

    hub = groups["Hub"]
    # Round base
    m = Mesh("hub_base")
    outline = ring(HUB, HUB_R, 20)
    top_uv = atlas.canvas(HUB[0] - HUB_R, HUB[0] + HUB_R, HUB[1] - HUB_R, HUB[1] + HUB_R, hub_top, flip_b=True)
    bottom_uv = atlas.canvas(HUB[0] - HUB_R, HUB[0] + HUB_R, HUB[1] - HUB_R, HUB[1] + HUB_R, hub_bottom, True)
    centre_top, centre_bottom = (HUB_TOP, *HUB), (X0, *HUB)
    for k in range(len(outline)):
        a, b = outline[k], outline[(k + 1) % len(outline)]
        tri = [centre_top, (HUB_TOP, *a), (HUB_TOP, *b)]
        m.face(tri, [top_uv(p[1], p[2]) for p in tri])
        tri = [centre_bottom, (X0, *b), (X0, *a)]
        m.face(tri, [bottom_uv(p[1], p[2]) for p in tri])
        m.face([(HUB_TOP, *a), (HUB_TOP, *b), (X0, *b), (X0, *a)], atlas.swatch(GREY_MID))
    hub.append(m)

    # Lamps round the back of the rim
    lit = atlas.canvas(0, 1, 0, 1, lambda a_, b_: (LAMP, LAMP_GLOW))
    for k, angle in enumerate((40, 65, 90, 115, 140)):
        a = math.radians(angle)
        radial, tangent = (math.cos(a), math.sin(a)), (-math.sin(a), math.cos(a))
        pts = []
        for rr, tt in ((HUB_R - 0.2, -0.45), (HUB_R - 0.2, 0.45), (HUB_R + 0.35, 0.45), (HUB_R + 0.35, -0.45)):
            pts.append((HUB[0] + rr * radial[0] + tt * tangent[0], HUB[1] + rr * radial[1] + tt * tangent[1]))
        lamp = Mesh(f"lamp_{k + 1}")
        solid(lamp, at_x(pts, X0 + 0.95), at_x(pts, X0 + 0.25), lambda p: lit(0.5, 0.5), plain(atlas, TRIM), LAMP,
              atlas)
        hub.append(lamp)

    # Red centre and the red arm reaching out to the blade (body plus two prongs)
    m = Mesh("red_centre")
    centre_uv = atlas.canvas(HUB[0] - 2.1, HUB[0] + 2.1, HUB[1] - 2.1, HUB[1] + 2.1, red_disc, flip_b=True)
    disc = ring(HUB, 2.1, 14)
    solid(m, at_x(disc, RED_TOP), at_x(disc, HUB_TOP), lambda p: centre_uv(p[1], p[2]), plain(atlas, RED_DARK),
          RED_DARK, atlas)
    hub.append(m)

    y = HUB[0]
    arm_uv = atlas.canvas(y - 2.6, y + 2.6, -7.2, -0.5, red_arm, flip_b=True)
    body = [(y - 1.9, -1.0), (y + 1.9, -1.0), (y + 1.5, -4.6), (y + 2.5, -5.6), (y - 2.5, -5.6), (y - 1.5, -4.6)]
    prongs = [[(y - 2.5, -5.6), (y - 0.55, -5.6), (y - 0.75, -7.0), (y - 2.2, -7.0)],
              [(y + 0.55, -5.6), (y + 2.5, -5.6), (y + 2.2, -7.0), (y + 0.75, -7.0)]]
    for name, shape in (("red_arm", body), ("red_prong_1", prongs[0]), ("red_prong_2", prongs[1])):
        m = Mesh(name)
        solid(m, at_x(shape, RED_TOP + 0.1), at_x(shape, HUB_TOP), lambda p: arm_uv(p[1], p[2]),
              plain(atlas, RED_DARK), RED_DARK, atlas)
        hub.append(m)
    plate = [(y - 0.8, -2.3), (y + 0.8, -2.3), (y + 0.8, -4.5), (y - 0.8, -4.5)]
    plate_uv = atlas.canvas(y - 0.8, y + 0.8, -4.5, -2.3, indicator, flip_b=True)
    m = Mesh("indicator")
    solid(m, at_x(plate, RED_TOP + 0.4), at_x(plate, RED_TOP + 0.1), lambda p: plate_uv(p[1], p[2]),
          plain(atlas, GREY_MID), GREY_MID, atlas)
    hub.append(m)

    # LP counter across the hub, along the arm
    y0, y1, z0, z1 = BAR
    bar = [(y0, -0.6), (y0 + 0.6, z0), (y1 - 0.6, z0), (y1, -0.6), (y1, 0.6), (y1 - 0.6, z1), (y0 + 0.6, z1),
           (y0, 0.6)]
    bar_uv = atlas.canvas(y0, y1, z0, z1, lp_bar, flip_b=True)
    m = Mesh("lp_counter")
    solid(m, at_x(bar, BAR_TOP), at_x(bar, RED_TOP), lambda p: bar_uv(p[1], p[2]), plain(atlas, GREY_MID),
          GREY_MID, atlas)
    hub.append(m)

    # Deck holder on the hand side, with a deck in it
    base = HUB_TOP
    hub.append(box("deck_holder", atlas, (base, 9.0, -1.8), (base + 0.4, 13.6, 1.8), GREY_MID))
    hub.append(box("deck_wall_1", atlas, (base + 0.4, 9.0, -1.8), (base + 1.1, 13.6, -1.4), GREY))
    hub.append(box("deck_wall_2", atlas, (base + 0.4, 9.0, 1.4), (base + 1.1, 13.6, 1.8), GREY))
    hub.append(box("deck_wall_3", atlas, (base + 0.4, 9.0, -1.4), (base + 1.1, 9.4, 1.4), GREY))
    deck_uv = atlas.canvas(9.4, 13.4, -1.35, 1.35, card_back, flip_b=True)
    hub.append(box("deck", atlas, (base + 0.4, 9.4, -1.35), (base + 1.25, 13.4, 1.35), CARD_EDGE,
                   top=lambda p: deck_uv(p[1], p[2])))

    # Graveyard tray on the elbow side
    tray_uv = atlas.canvas(19.0, 23.2, -1.7, 1.7, tray_top, flip_b=True)
    hub.append(box("graveyard_tray", atlas, (X0 + 0.5, 19.0, -1.7), (X0 + 1.1, 23.2, 1.7), GREY_MID,
                   top=lambda p: tray_uv(p[1], p[2])))

    # The blade. Duel pose: the 3-zone wing runs from the joint towards the elbow, the 2-zone wing from the joint
    # towards the hand, bent BEND degrees back towards the arm.
    c, s = math.cos(math.radians(BEND)), math.sin(math.radians(BEND))
    joint = (JOINT_Y, BLADE_INNER)
    duel_three = ((1.0, 0.0), (0.0, -1.0))
    duel_two = ((-c, s), (-s, -c))
    three_len = Wing("x", 3, joint, *duel_three).inner_end + BLADE_W * math.tan(math.radians(BEND / 2))
    # Standby: the 3-zone wing straight in front of the hub, the 2-zone wing mirrored behind it.
    slide = HUB[0] - (JOINT_Y - BLADE_W * math.tan(math.radians(BEND / 2)) + three_len / 2)
    three = Wing("WingThree", 3, (JOINT_Y + slide, BLADE_INNER), *duel_three)
    probe = Wing("x", 2, (0, 0), (1, 0), (0, 1))
    two_len = probe.inner_end + BLADE_W * probe.miter
    two = Wing("WingTwo", 2, (HUB[0] - two_len / 2 + BLADE_W * probe.miter, -BLADE_INNER), (1.0, 0.0), (0.0, 1.0))
    groups["WingThree"] += three.meshes(atlas)
    groups["WingTwo"] += two.meshes(atlas)

    # Deploy: WingThree slides `slide` back along the arm; WingTwo turns about the point that carries its standby
    # frame onto its duel frame (a plane rotation by phi).
    rot = (duel_two[0], duel_two[1])  # images of standby s (1,0) and t (0,1)
    phi = math.degrees(math.atan2(rot[0][1], rot[0][0]))  # angle from +y towards +z
    a = [[rot[0][0], rot[1][0]], [rot[0][1], rot[1][1]]]
    d = (joint[0] - (a[0][0] * two.origin[0] + a[0][1] * two.origin[1]),
         joint[1] - (a[1][0] * two.origin[0] + a[1][1] * two.origin[1]))
    # centre: (I - A) c = d
    m00, m01, m10, m11 = 1 - a[0][0], -a[0][1], -a[1][0], 1 - a[1][1]
    det = m00 * m11 - m01 * m10
    pivot = ((d[0] * m11 - m01 * d[1]) / det, (m00 * d[1] - m10 * d[0]) / det)
    return atlas, groups, slide, phi, pivot


def animator(name, frames):
    keys = []
    for channel, time, value in frames:
        keys.append({"channel": channel, "data_points": [{k: str(r4(v)) for k, v in zip("xyz", value)}],
                     "uuid": uid(), "time": time, "color": -1, "interpolation": "catmullrom"})
    return {"name": name, "type": "bone", "keyframes": keys}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=HERE / "duel_disk.bbmodel")
    args = parser.parse_args()

    atlas, parts, slide, phi, pivot = build()

    def scaled(p):
        return (X0 + (p[0] - X0) * SCALE, HUB[0] + (p[1] - HUB[0]) * SCALE, HUB[1] + (p[2] - HUB[1]) * SCALE)
    for name, meshes in parts.items():
        if name != "Bracer":
            for mesh in meshes:
                mesh.verts = [scaled(v) for v in mesh.verts]
    pivot = scaled((X0, *pivot))[1:]
    slide *= SCALE
    hub_origin = [X0, HUB[0], HUB[1]]
    origins = {"LeftArm": [5, 22, 0], "DuelDisk": hub_origin, "Bracer": hub_origin, "Hub": hub_origin,
               "WingThree": hub_origin, "WingTwo": [X0, r4(pivot[0]), r4(pivot[1])]}
    groups = {name: {"name": name, "origin": [r4(c) for c in o], "rotation": [0, 0, 0], "uuid": uid(),
                     "export": True, "visibility": True} for name, o in origins.items()}
    elements, children = [], {name: [] for name in groups}
    for name, meshes in parts.items():
        for mesh in meshes:
            e = mesh.element()
            elements.append(e)
            children[name].append(e["uuid"])

    # Blockbench shows a keyframe rotation x as -x and a position x as -x (see DiskModel.boneTransforms).
    zero = (0, 0, 0)
    turn = (-phi, 0, 0)
    half_turn = (-phi / 2, 0, 0)
    lifted = (-LIFT * SCALE, 0, 0)
    out = (0, slide * -1, 0)  # position y is applied as is: slide the wing back by -slide
    three = groups["WingThree"]["uuid"]
    two = groups["WingTwo"]["uuid"]
    deploy = {
        three: animator("WingThree", [("position", 0, zero), ("position", 0.35, out), ("position", 1, out)]),
        two: animator("WingTwo", [("rotation", 0, zero), ("rotation", 0.25, zero), ("rotation", 0.6, half_turn),
                                  ("rotation", 0.95, turn), ("rotation", 1, turn),
                                  ("position", 0, zero), ("position", 0.25, zero), ("position", 0.6, lifted),
                                  ("position", 0.95, zero), ("position", 1, zero)]),
    }
    fold = {
        two: animator("WingTwo", [("rotation", 0, turn), ("rotation", 0.05, turn), ("rotation", 0.4, half_turn),
                                  ("rotation", 0.75, zero), ("rotation", 1, zero),
                                  ("position", 0, zero), ("position", 0.05, zero), ("position", 0.4, lifted),
                                  ("position", 0.75, zero), ("position", 1, zero)]),
        three: animator("WingThree", [("position", 0, out), ("position", 0.65, out), ("position", 1, zero)]),
    }

    def outline(name, inner):
        return {"uuid": groups[name]["uuid"], "isOpen": False, "children": inner + children[name]}

    tree = [outline("LeftArm", [outline("DuelDisk", [outline("Bracer", []), outline("Hub", []),
                                                     outline("WingThree", []), outline("WingTwo", [])])])]

    def texture(name, image, index):
        buf = io.BytesIO()
        image.save(buf, "PNG")
        return {"name": name, "folder": "", "namespace": "", "id": str(index), "width": ATLAS[0],
                "height": ATLAS[1], "uv_width": ATLAS[0], "uv_height": ATLAS[1], "particle": False,
                "render_mode": "default", "render_sides": "auto", "visible": True, "internal": True,
                "saved": False, "uuid": uid(),
                "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}

    def animation(name, animators):
        return {"uuid": uid(), "name": name, "loop": "hold", "override": False, "length": 1, "snapping": 20,
                "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                "animators": animators}

    model = {
        "meta": {"format_version": "4.10", "model_format": "figura", "box_uv": False},
        "name": "duel_disk",
        "model_identifier": "",
        "visible_box": [1, 1, 0],
        "resolution": {"width": ATLAS[0], "height": ATLAS[1]},
        "elements": elements,
        "groups": list(groups.values()),
        "outliner": tree,
        "textures": [texture("duel_disk.png", atlas.base, 0), texture("duel_disk_e.png", atlas.glow, 1)],
        "animations": [animation("deploy", deploy), animation("fold", fold)],
    }
    args.out.write_text(json.dumps(model, indent=1), encoding="utf-8")
    print(f"wrote {args.out.name}: {len(elements)} meshes, slide {slide:.2f}, swing {phi:.1f} deg about "
          f"y {pivot[0]:.2f} z {pivot[1]:.2f}")


if __name__ == "__main__":
    main()
