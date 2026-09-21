#!/usr/bin/env python3
"""Generate the GPDb app-icon artwork as a 1024x1024 SVG.

This is the single source of truth for the icon. build-icon.sh runs it, feeds the
result to actool, and out comes Assets.car + AppIcon.icns — so the .svg is never
edited by hand and can never drift from the document that compiles it.

Three things about this renderer (CoreSVG, reached through Icon Composer's ictool)
shaped the file, and each of them cost a debugging round to find:

  1. `oklch()` is not supported and does NOT error — it paints near-black. Every colour
     here is hex plus a separate opacity attribute.
  2. The `.icon` canvas maps 1024 units to the full icon, so the artwork is authored at
     1024x1024 and needs no scale factor.
  3. The glyph is composed through a `<mask>`, not a `clipPath` with `fill-rule`. An
     evenodd clip cannot express "ring, minus a gap, plus a bar back on top" — the
     re-added bar lands inside the gap and flips the parity back to cut. A mask is
     additive: white keeps, black cuts, and the order of the shapes is the only rule.

The glyph is therefore built as: a white ring, a black aperture wedge over its upper
right, and a white crossbar put back at mid-height.

On colour: the plate is set in icon.json, not here, because the system derives the
dark and tinted plates from it by itself. What that means for this file is that the
glyph has to hold its own against a range of grounds — so its darkest tone is kept
clear of the plate's mid-tone rather than tuned to match a particular background.
"""
import math
import os

SIZE = 1024
C = SIZE / 2

R_OUT = 300.0        # outer edge of the ring
R_IN = 170.0         # inner edge — a 130-unit band, ~13% of the icon

# The aperture: everything right of APERTURE_X and above APERTURE_TOP is cut away, which
# opens the ring's upper right. APERTURE_TOP is what sets how far down the gap reaches.
APERTURE_X = 512 + 78
APERTURE_TOP = C - 44

# The crossbar, put back across the gap at mid-height. Its left end sits inside the hole
# so the bar reads as a bar rather than as a chord of the ring.
BAR_LEFT = 512 + 92
BAR_TOP = C - 50
BAR_BOTTOM = C + 64

# The stem rising from the bar's right end to form the G's terminal. Without it the
# glyph is a C with a bar, which at 16px is one letter away from an e.
STEM_INNER = 512 + R_OUT - 122
STEM_TOP = C - 186

LIGHT = '#ffffff'
SHADE = '#4a2c06'    # warm, not neutral: a grey shade over amber reads as dirt

# Facet planes as annular sectors over the ring band. Light where the pane catches the
# light source at the top-left, shade where it falls away. Angles run clockwise from
# 3 o'clock, matching SVG's y-down coordinates.
FACETS = [
    (148, 186, LIGHT, 0.52),
    (186, 224, LIGHT, 0.26),
    (224, 262, LIGHT, 0.07),
    (262, 300, SHADE, 0.12),
    (300, 338, SHADE, 0.20),
    (338, 372, SHADE, 0.28),
    (12, 46, SHADE, 0.30),
    (46, 84, SHADE, 0.22),
    (84, 122, SHADE, 0.12),
    (122, 148, LIGHT, 0.28),
]
# Seams sit on the boundaries between adjacent facets. They are what makes the planes
# read as cut crystal instead of as a soft gradient.
SEAMS = [148, 186, 224, 262, 300, 338, 46, 84, 122]


def pt(r, deg):
    a = math.radians(deg)
    return (C + r * math.cos(a), C + r * math.sin(a))


def sector(a0, a1, r0, r1):
    """An annular sector, drawn as a quadrilateral. Facets want straight edges."""
    o0, o1 = pt(r1, a0), pt(r1, a1)
    i1, i0 = pt(r0, a1), pt(r0, a0)
    return (f'M{o0[0]:.2f} {o0[1]:.2f}L{o1[0]:.2f} {o1[1]:.2f}'
            f'L{i1[0]:.2f} {i1[1]:.2f}L{i0[0]:.2f} {i0[1]:.2f}Z')


def rect(x0, y0, x1, y1):
    return (f'M{x0:.2f} {y0:.2f}L{x1:.2f} {y0:.2f}'
            f'L{x1:.2f} {y1:.2f}L{x0:.2f} {y1:.2f}Z')


def build():
    p = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" '
        f'viewBox="0 0 {SIZE} {SIZE}">',
        '<defs>',

        # The glyph, as a mask. White keeps, black cuts, order is the only rule.
        '<mask id="glyph">',
        f'<rect x="0" y="0" width="{SIZE}" height="{SIZE}" fill="black"/>',
        f'<circle cx="{C}" cy="{C}" r="{R_OUT}" fill="white"/>',
        f'<circle cx="{C}" cy="{C}" r="{R_IN}" fill="black"/>',
        f'<path d="{rect(APERTURE_X, -400, 1400, APERTURE_TOP)}" fill="black"/>',
        f'<path d="{rect(STEM_INNER, STEM_TOP, C + R_OUT + 4, BAR_TOP + 6)}" fill="white"/>',
        f'<path d="{rect(BAR_LEFT, BAR_TOP, C + R_OUT + 4, BAR_BOTTOM)}" fill="white"/>',
        '</mask>',

        # The crystal body: near-white champagne where the light hits, amber away from
        # it. The dark end stops at #e3b155 rather than going deeper — anything darker
        # lands on the plate's own mid-tone and the ring's lower right dissolves into
        # the background. The shading that reads as depth comes from the facets instead.
        '<linearGradient id="body" x1="0.16" y1="0.04" x2="0.88" y2="0.98">',
        '<stop offset="0" stop-color="#fffdf7"/>',
        '<stop offset="0.28" stop-color="#fdf0cd"/>',
        '<stop offset="0.60" stop-color="#f6d888"/>',
        '<stop offset="1" stop-color="#e3b155"/>',
        '</linearGradient>',

        # Rim light and inner refraction, as gradients so they fade along their length
        # instead of stopping dead at a stroke end.
        '<linearGradient id="rim" x1="0.08" y1="0" x2="0.62" y2="0.92">',
        '<stop offset="0" stop-color="#ffffff" stop-opacity="1"/>',
        '<stop offset="0.42" stop-color="#fff3d4" stop-opacity="0.38"/>',
        '<stop offset="1" stop-color="#fff3d4" stop-opacity="0"/>',
        '</linearGradient>',
        '<linearGradient id="inner" x1="0.92" y1="0.98" x2="0.34" y2="0.28">',
        '<stop offset="0" stop-color="#5c3400" stop-opacity="0.50"/>',
        '<stop offset="0.55" stop-color="#5c3400" stop-opacity="0.07"/>',
        '<stop offset="1" stop-color="#5c3400" stop-opacity="0"/>',
        '</linearGradient>',
        '</defs>',

        '<g mask="url(#glyph)">',
        f'<rect x="0" y="0" width="{SIZE}" height="{SIZE}" fill="url(#body)"/>',
    ]

    for a0, a1, colour, alpha in FACETS:
        # Sectors run past both edges of the band; the mask trims them back to the glyph,
        # so the facet geometry never has to agree exactly with the ring.
        p.append(f'<path d="{sector(a0, a1, R_IN - 40, R_OUT + 40)}" fill="{colour}" '
                 f'fill-opacity="{alpha}"/>')

    for deg in SEAMS:
        x0, y0 = pt(R_IN - 4, deg)
        x1, y1 = pt(R_OUT + 4, deg)
        p.append(f'<path d="M{x0:.2f} {y0:.2f}L{x1:.2f} {y1:.2f}" stroke="#ffffff" '
                 f'stroke-opacity="0.42" stroke-width="3.5" fill="none" '
                 f'stroke-linecap="round"/>')

    # Rim light along the outer top-left, inner refraction opposite it, and a tight
    # outer edge line so the glyph has a defined boundary against the plate.
    p.append(f'<circle cx="{C}" cy="{C}" r="{R_OUT - 8}" fill="none" '
             f'stroke="url(#rim)" stroke-width="17"/>')
    p.append(f'<circle cx="{C}" cy="{C}" r="{R_IN + 10}" fill="none" '
             f'stroke="url(#inner)" stroke-width="22"/>')
    p.append(f'<circle cx="{C}" cy="{C}" r="{R_OUT - 2}" fill="none" '
             f'stroke="#ffffff" stroke-opacity="0.30" stroke-width="4"/>')

    p.append('</g>')
    p.append('</svg>')
    return '\n'.join(p)


if __name__ == '__main__':
    import sys
    here = os.path.dirname(os.path.abspath(__file__))
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        here, 'AppIcon.icon', 'Assets', 'G.svg')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w') as f:
        f.write(build())
    print(f'wrote {out}')
