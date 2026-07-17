import { hexDistance, hexInBounds, hexNeighbors, reachableHexes } from './hex-util';

describe('hex-util — Hexgitter-Mathematik (Spiegel von HexUtil.java)', () => {

  it('hexDistance: bekannte Werte und Symmetrie', () => {
    expect(hexDistance(2, 2, 2, 2)).toBe(0);
    expect(hexDistance(2, 2, 3, 2)).toBe(1);
    expect(hexDistance(0, 0, 5, 0)).toBe(5);
    expect(hexDistance(1, 3, 6, 5)).toBe(hexDistance(6, 5, 1, 3));
  });

  it('hexNeighbors: alle 6 Nachbarn haben Distanz 1 (gerade und ungerade Zeile)', () => {
    for (const [q, r] of [[4, 3], [4, 4]] as Array<[number, number]>) {
      const n = hexNeighbors(q, r);
      expect(n.length).toBe(6);
      for (const [nq, nr] of n) {
        expect(hexDistance(q, r, nq, nr)).toBe(1);
      }
    }
  });

  it('hexInBounds prüft die Kartengrenzen', () => {
    expect(hexInBounds(0, 0, 10, 8)).toBe(true);
    expect(hexInBounds(9, 7, 10, 8)).toBe(true);
    expect(hexInBounds(10, 0, 10, 8)).toBe(false);
    expect(hexInBounds(0, -1, 10, 8)).toBe(false);
  });

  it('reachableHexes: ohne Hindernisse entspricht die Kostenzahl der Hexdistanz', () => {
    const reach = reachableHexes(3, 3, 2, 10, 8, () => false);
    expect(reach.has('3,3')).toBe(false); // Startfeld nicht enthalten
    for (const [key, cost] of reach) {
      const [q, r] = key.split(',').map(Number);
      expect(cost).toBe(hexDistance(3, 3, q, r));
    }
    // Budget 2 → niemals Distanz > 2
    expect([...reach.values()].every(c => c <= 2)).toBe(true);
  });

  it('reachableHexes: blockierte Felder sind ausgeschlossen und erzwingen Umwege', () => {
    // Senkrechter Wandriegel bei q=4 — Felder rechts davon nur über den Umweg erreichbar
    const wall = (q: number, r: number) => q === 4 && r >= 0 && r <= 6;
    const reach = reachableHexes(3, 3, 3, 10, 8, wall);
    expect(reach.has('4,3')).toBe(false); // Wandfeld selbst
    // Direkt hinter der Wand (5,3) wäre Distanz 2 — durch den Riegel teurer oder gar nicht drin
    const cost = reach.get('5,3');
    expect(cost === undefined || cost > 2).toBe(true);
  });

  it('reachableHexes: Budget 0 liefert nichts', () => {
    expect(reachableHexes(3, 3, 0, 10, 8, () => false).size).toBe(0);
  });
});
