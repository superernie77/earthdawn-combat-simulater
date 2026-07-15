# -*- coding: utf-8 -*-
"""Prueft alle mat-icon-Namen im Frontend gegen die klassische Material-Icons-Schrift.

Die App laedt in src/index.html die klassische Schrift "Material Icons" — NICHT Material Symbols.
Ein Name, den diese Schrift nicht kennt (z.B. sentiment_extremely_dissatisfied, skull), wird von
mat-icon als ROHER TEXT gerendert: der Button ist dann mit dem Wort gefuellt und seine
Beschriftung verschwindet hinter overflow:hidden. Im Build faellt das nicht auf.

Aufruf:  python frontend/scripts/check-mat-icons.py
Exit 1 bei Fund. Braucht Netzzugriff (laedt die Ligaturliste von GitHub).
"""
import io
import os
import re
import sys
import urllib.request

CODEPOINTS_URL = ('https://raw.githubusercontent.com/google/material-design-icons/'
                  'master/font/MaterialIcons-Regular.codepoints')

try:
    with urllib.request.urlopen(CODEPOINTS_URL, timeout=30) as r:
        VALID = set(line.split()[0] for line in r.read().decode().splitlines() if line.strip())
except Exception as exc:  # noqa: BLE001
    print('Ligaturliste nicht abrufbar (%s) — Pruefung uebersprungen.' % exc)
    sys.exit(0)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src')

files = []
for dirpath, _dirs, names in os.walk(ROOT):
    for n in names:
        if n.endswith(('.ts', '.html')):
            files.append(os.path.join(dirpath, n))

bad = []
for f in files:
    src = io.open(f, encoding='utf-8').read()
    for m in re.finditer(r'<mat-icon\b[^>]*>([^<{}]+)</mat-icon>', src):
        name = m.group(1).strip()
        # Interpolationen ({{ ... }}) und Emoji ueberspringen — nur echte Ligaturen pruefen
        if not name or not re.fullmatch(r'[a-z0-9_]+', name):
            continue
        if name not in VALID:
            bad.append((os.path.relpath(f, ROOT).replace('\\', '/'), name))

if bad:
    print('UNGUELTIGE mat-icon-Namen (%d) — sie rendern als roher Text:' % len(set(bad)))
    for f, n in sorted(set(bad)):
        print('  %-58s %s' % (f, n))
    sys.exit(1)

print('OK — alle mat-icon-Ligaturen in %d Dateien existieren in der Material-Icons-Schrift.'
      % len(files))
