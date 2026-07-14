# Release Notes

## v1.2.0 (in Entwicklung)

**Kombattanten-Kachel: zwei Spalten**
Die Kopfzeile (Initiative, Name, Disziplin, Zustands-Badges und *Auto*) steht jetzt über der vollen Breite. Darunter stehen links die Werte — Schadensleiste, Wunden, Karma, Verteidigungen, Rüstung und aktive Effekte — und rechts alle Aktionsbuttons untereinander.

**Jeder Aktionsbutton hat jetzt eine Beschriftung.** Vorher waren die meisten reine Icons, deren Bedeutung nur der Tooltip verriet. Zu lange Namen werden gekürzt statt aus dem Button zu laufen; der vollständige Text steht weiterhin im Tooltip. Der Button *Furcht abschütteln* ist nicht mehr grellgrün, sondern in gedämpftem Türkis wie die übrigen Buttons.

**Freier Zusatzfaden aus der Erweiterten Matrize**
Liegt ein Zauber **ohne Pflichtfäden** (z.B. Blitz) in einer Erweiterten Matrize, hat deren vorgewobener Faden nichts zu tun — er wird automatisch zum Zusatzfaden mit **Wirkung Verstärken (Wirkungsstufe +2)**. Ohne Wurf, ohne Aktion, und er zählt **nicht** gegen die Obergrenze: ein Adept mit Fadenweben-Rang 1 kann also den freien *und* einen selbst gewobenen Zusatzfaden nutzen. Zauber, die keine Wirkungsstufe kennen (z.B. Katastrophe), gehen leer aus.

**Zusätzliche Fäden bei Zaubern**
Sind alle Pflichtfäden eines Zaubers gewoben, kann jeder weitere Faden eine der Zusatz-Optionen des Zaubers kaufen — auswählbar im Fadenweben-Dialog. Die Obergrenze ist der **Fadenweben-Rang**; dieselbe Option darf mehrfach gewählt werden. Auch **Sofortzauber ohne Pflichtfäden** (z.B. Blitz) können Zusatzfäden aufnehmen.

Automatisch verrechnet wird nur **„Wirkung Verstärken (Wirkungsstufe +2)"**. Alle übrigen Optionen — Reichweite, zusätzliche Ziele, Wirkungsdauer und Boni auf Heimlichkeit/Wahrnehmung — werden gewählt, gespeichert und im Kampfprotokoll sowie im Ergebnisfenster angezeigt, aber bewusst **nicht** gerechnet: dafür fehlen dem Kampfsystem die Grundlagen (kein Distanzsystem, einzelzielige Zauber, Dauer in Runden statt Minuten, keine Nicht-Kampf-Proben). Der Spielleiter entscheidet.

Hinterlegt für 10 Illusionisten-Zauber: Katastrophe, Umhang, Vertrauen, Blitz, Illusionärer Blitz, Blindheit, Gedankennebel, Sehen von Verborgenem, Niemand Da, Phantomkrieger.

**Neues Talent: Magie neutralisieren**
Beendet einen beliebigen aktiven Effekt auf einem beliebigen Kombattanten (WIL + Rang vs. **Effektstufe + 10**). Verbraucht die Aktion der Runde und kostet 1 Überanstrengung. Beim Anwenden öffnet sich **bei allen Mitspielern** ein Auswahldialog mit allen aktuell aktiven Effekten; dort wird der Effekt gewählt und seine Stufe eingetragen (Effekte haben keine eigene Stufe — maßgeblich ist der auslösende Zauber bzw. das Talent). Das Ergebnis erscheint ebenfalls bei allen. Welche Effekte sich neutralisieren lassen, entscheidet der Spielleiter — es stehen alle zur Auswahl.

**Neues Talent: Verängstigen**
Der Adept jagt einem Gegner übernatürliche Furcht ein (WIL + Rang vs. Mystische Verteidigung, Standardaktion, 0 Überanstrengung). Bei Erfolg erleidet das Ziel **−2 auf alle Aktionsproben je Erfolg** für Rang Runden. Das Ziel darf in jeder seiner Runden eine Willenskraftprobe gegen die Verängstigen-Stufe ablegen („Furcht abschütteln") — Erfolg beendet den Effekt vorzeitig. Disziplintalent des Geisterbeschwörers (1. Kreis), Talentoption für Illusionisten (Kreis 5–8).

**Bugfix: Schwimmen-Talent überlebt Neustarts**
Das Talent Schwimmen wurde beim Backend-Start fälschlich als „nicht implementiert" gelöscht (inklusive Charakter-Zuweisungen) und neu angelegt — es bleibt jetzt erhalten.

**Arzt-Umbau: zwei Behandlungsmodi**
Die Arzt-Fertigkeit unterscheidet jetzt zwei Anwendungen:
- **Verletzungen behandeln** (verlorene LP): nur 1× pro Erholungsprobe — Erfolg gibt +Rang auf den nächsten Erholungswurf.
- **Wunde versorgen**: unterdrückt den −1-Wundmalus einer Wunde bei Erholungsproben — mehrfach anwendbar, bis alle Wunden versorgt sind (Verband bleibt bestehen).

Beide Modi würfeln WAH-Stufe + Rang gegen MW 5 und verbrauchen je 1× Verbandszeug — auch bei Fehlschlag.

## v1.1.0

### 🆕 Neue Fähigkeiten & Talente

**Schwanzangriff (T'skrang-Rassenfähigkeit)**
Ein T'skrang kann einen zusätzlichen waffenlosen Angriff mit dem Schwanz ausführen (1×/Runde, verbraucht nicht die Hauptaktion) – Probe über Waffenloser Kampf, Schaden nach Stärkestufe. Eine Nahkampfwaffe lässt sich als **Schwanzwaffe** (🦎) am Schwanz befestigen. Der Einsatz ist riskant: **−2 auf alle Proben in dieser Runde**.

**Schwimmen (Talent + Fertigkeit)**
Neues STÄ-basiertes *Schwimmen* – sowohl als Talent als auch als Fertigkeit. Dazu der magische Gegenstand **Schwimmkristall**: +3 auf Schwimmen und „Erlaubt Unterwasseratmung von Rang Minuten".

### ⚔️ Kampf-Verbesserungen

**Waffen einem Angriffstalent zuordnen**
Eine Waffe kann fest an ein Angriffstalent/-fertigkeit gebunden werden (Nahkampf-/Projektil-/Wurfwaffen, Waffenloser Kampf). Im Kampf werden dann nur die passenden Waffen angeboten. Bestehende (nicht zugeordnete) Waffen bleiben überall wählbar – vollständig rückwärtskompatibel.

**Angriffsdialog aufgeräumt**
Im Angriffsdialog stehen nur noch die vier Waffen-Angriffstalente (+ Waffen-Fertigkeiten) zur Auswahl – fremde Talente und Spruchzauberei sind raus.

**Zauberauswahl nur aus Matrizen**
Im Kampf werden beim Fadenweben und Wirken nur Zauber angeboten, die in einer Zaubermatrize (normal oder erweitert) liegen.

**Reichhaltigeres Kampfprotokoll**
Das Protokoll ist jetzt chronologisch absteigend (neueste Einträge oben) und zeigt bei Angriffen die genauen Würfelergebnisse für Angriff und Schaden (Einzelwürfel, Karmawürfel, Summe), den Strain sowie alle Modifikatoren.

**Kampfende an alle Clients**
Beim Beenden eines Kampfes erscheint nun ein synchronisiertes „Kampf beendet"-Fenster bei allen Zuschauern (plus dauerhaftes 🏁-Badge) – niemand bleibt mehr auf einem eingefrorenen Bildschirm.

**Übersichtlichere Würfelanzeige**
Erholungsproben zeigen die einzelnen Würfel (inkl. Karmawürfel und Gesamtsumme); der Karmawürfel bei der Initiative wird als eigener Würfel dargestellt statt als Bonus-Chip.

### 🎲 Karma-Optionen

- **Karma auf Initiative** – Dieb, Kundschafter, Luftsegler und Schütze dürfen ab dem 3. Kreis 1 Karma für +W6 auf die Initiative einsetzen (Button in der Ansagephase).
- **Karma auf Erholungsproben** – Elementarist, Krieger, Luftpirat, Tiermeister und Waffenschmied ab dem 3. Kreis, Kundschafter ab dem 5. Kreis, dürfen 1 Karma für +W6 auf eine Erholungsprobe einsetzen (Checkbox auf der Erholung-Seite).

### 🎭 Spielleiter-Werkzeuge

Zwei neue GM-Bedingungen (manuell aktivierbar, da Position/Anzahl nicht automatisch berechenbar):
- **Toter Winkel** (Angriff von hinten): −2 KV/MV, und das Ziel darf **keine aktiven Verteidigungstalente** (Ausweichen/Riposte) einsetzen.
- **Bedrängt**: −2 auf Angriffsproben, KV und MV. Jede weitere Quelle verstärkt die Mali kumulativ (Überwältigt).

### ⚙️ Charakterbogen

**Konfigurierbare Boni** – zusätzlich zu den Verteidigungs-Boni gibt es jetzt Bonus/Malus-Steppers für **Lebenspunkte** (BW & TD), **Initiative** und **Erholungsstufe**.

### 🐛 Bugfixes

- **Ausweichen/Riposte-Ergebnis** wird jetzt an alle Zuschauer synchronisiert (vorher nur beim ausführenden Spieler sichtbar).
- **Lufttanz-Zusatzangriff** erscheint jetzt auch bei einem Fehlschlag – entscheidend ist allein der Initiative-Vorsprung ≥ 10, nicht der Treffer.

---

## v1.0.0

Erstveröffentlichung: Charakterverwaltung, Ausrüstung, Talente/Fertigkeiten/Zauber und vollständiges ED4-Rundenkampfsystem mit Live-Updates über WebSocket.
