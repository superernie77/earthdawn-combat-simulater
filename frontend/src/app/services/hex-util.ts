/**
 * Hexgitter-Mathematik für die Kampfkarte — Spiegel von backend HexUtil.java.
 *
 * Odd-r-Gitter mit spitzen Hexes: q = Spalte (0..width-1), r = Zeile (0..height-1).
 * Die Zeilenparität bestimmt den Spaltenversatz der Nachbarn.
 */

const NEIGHBORS_EVEN: ReadonlyArray<readonly [number, number]> = [
  [+1, 0], [0, -1], [-1, -1], [-1, 0], [-1, +1], [0, +1]
];
const NEIGHBORS_ODD: ReadonlyArray<readonly [number, number]> = [
  [+1, 0], [+1, -1], [0, -1], [-1, 0], [0, +1], [+1, +1]
];

export function hexNeighbors(q: number, r: number): Array<[number, number]> {
  const offsets = r % 2 === 0 ? NEIGHBORS_EVEN : NEIGHBORS_ODD;
  return offsets.map(([dq, dr]) => [q + dq, r + dr]);
}

export function hexDistance(q1: number, r1: number, q2: number, r2: number): number {
  const x1 = q1 - (r1 - (r1 & 1)) / 2;
  const z1 = r1;
  const y1 = -x1 - z1;
  const x2 = q2 - (r2 - (r2 & 1)) / 2;
  const z2 = r2;
  const y2 = -x2 - z2;
  return (Math.abs(x1 - x2) + Math.abs(y1 - y2) + Math.abs(z1 - z2)) / 2;
}

export function hexInBounds(q: number, r: number, width: number, height: number): boolean {
  return q >= 0 && q < width && r >= 0 && r < height;
}

/**
 * Erreichbare Felder per BFS (Spiegel der Backend-Regel): Hindernisse und besetzte Felder
 * blockieren, maximal `maxCost` Schritte. Liefert Map "q,r" → Kosten (Startfeld nicht enthalten).
 */
export function reachableHexes(
  startQ: number, startR: number, maxCost: number,
  width: number, height: number,
  isBlocked: (q: number, r: number) => boolean
): Map<string, number> {
  const out = new Map<string, number>();
  const visited = new Set<string>([startQ + ',' + startR]);
  let frontier: Array<[number, number]> = [[startQ, startR]];
  for (let cost = 1; cost <= maxCost && frontier.length > 0; cost++) {
    const next: Array<[number, number]> = [];
    for (const [q, r] of frontier) {
      for (const [nq, nr] of hexNeighbors(q, r)) {
        const key = nq + ',' + nr;
        if (visited.has(key)) continue;
        visited.add(key);
        if (!hexInBounds(nq, nr, width, height) || isBlocked(nq, nr)) continue;
        out.set(key, cost);
        next.push([nq, nr]);
      }
    }
    frontier = next;
  }
  return out;
}
