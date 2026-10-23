"""Convert a Phosphor SVG (paths only, 256x256) into an Android VectorDrawable.

Usage: python3 -I tools/svg2vd.py <in.svg> <out.xml>
"""
import re
import sys

src, dst = sys.argv[1], sys.argv[2]
svg = open(src).read()

paths = []
for attrs in re.findall(r"<path([^>]*)/?>", svg):
    d = re.search(r'\sd="([^"]+)"', attrs).group(1)
    op = re.search(r'opacity="([^"]+)"', attrs)
    alpha = f'\n        android:fillAlpha="{op.group(1)}"' if op else ""
    paths.append(f'    <path\n        android:fillColor="#FFFFFFFF"{alpha}\n        android:pathData="{d}" />')

open(dst, "w").write(
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    android:width="64dp" android:height="64dp"\n'
    '    android:viewportWidth="256" android:viewportHeight="256">\n'
    + "\n".join(paths)
    + "\n</vector>\n"
)
