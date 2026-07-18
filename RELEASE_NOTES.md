# Release Notes

## v1.2.0 (in Entwicklung)

**Phantomkrieger: Übererfolge und Zusatzfäden wirken jetzt mechanisch**
- **Übererfolge** beim Zauberwurf verlängern die Wirkungsdauer um **2 Runden je Übererfolg** (statt fix 3 Runden). Das gilt generell für Buff-/Debuff-Zauber mit „Dauer verlängern"-Übererfolg — auch die anderen Zauber aus dieser Liste profitieren.
- Der Zusatzfaden **„Wirkung Verstärken (+1 Bild)"** wird jetzt verrechnet: jedes zusätzliche Abbild gibt **+1 KV auf das Ziel und −1 auf Angriffe gegen das Ziel** — zusätzlich zu den +3/−3 der Grundwirkung, mehrfach wählbar (bis Fadenweben-Rang).

**Bugfix: Ausrüstungsformulare schnitten Felder ab**
In den Hinzufügen-Zeilen der Ausrüstung wurden die flexiblen Felder (Name, Beschreibung) von den festen Nachbarfeldern — etwa den neuen Kurz/Mittel/Weit-Reichweiten — auf Splitterbreite gequetscht. Alle flexiblen Felder haben jetzt eine Mindestbreite und brechen stattdessen in eine zweite Zeile um.

**Bugfix: Kampfprotokoll war falsch herum**
Das Backend liefert das Protokoll bereits mit den neuesten Einträgen zuerst; das Frontend drehte die Liste ein zweites Mal um — dadurch standen die ältesten Einträge oben. Jetzt wird explizit absteigend sortiert: die neuesten Einträge stehen oben.

**Hexfeld-Kampfkarte (optional)**
Der Kampf kann jetzt räumlich auf einer Hexfeld-Karte ausgetragen werden — als **optionale Zusatzschicht**: Ohne Aktivierung bleibt alles exakt wie bisher. Aktivierung per Checkbox beim Anlegen der Session (Größe wählbar, Standard 24×16) oder per Button im Setup; der „Karte"-Knopf öffnet die Karte **in einem eigenen Fenster**, das sich live mit dem Kampfscreen synchronisiert.

- **Spielleiter platziert**: Helden, Monster sowie Wände, Türen (zum Öffnen/Schließen), Bäume, Felsen und Möbel.
- **Bewegung in Initiative-Reihenfolge**: Jeder Kombattant läuft pro Runde bis zu seiner **Bewegungsrate** (neues Feld auf dem Charakterbogen, in Feldern). Eigenen Token anklicken → erreichbare Felder leuchten → Zielfeld anklicken. Hindernisse erzwingen Umwege (kürzester Weg zählt). Der Spielleiter darf jederzeit jeden versetzen.
- **Reichweiten steuern die Zielauswahl** im Kampfscreen: Nahkampf nur gegen angrenzende Felder, Projektil-/Wurfwaffen nach ihrer neuen **Kurz/Mittel/Weit-Reichweite**, Zauber nach ihrer neuen **Zauberreichweite**. Die Reichweiten filtern nur die Auswahl — die Kampfmechanik selbst bleibt unangetastet.
- **Animationen**: Nahkampfhiebe, fliegende Pfeile und Zauberbolzen werden auf der Karte animiert, sobald im Kampf gewürfelt wird — bei allen Zuschauern.

**Eigenes App-Icon**
Die App hat jetzt ein eigenes Zeichen: ein **Fadenring, von einem Schwert durchstoßen** — der Ring steht für das Fadenweben, die Kernmechanik von Earthdawn, das Schwert für den Kampf. Gehalten im Gold der App auf dunklem Grund. Es ist ein eigener Entwurf, nicht das FASA-Logo.

Das Zeichen erscheint im **Browser-Tab** und als **Logo in der Seitenleiste** neben dem Schriftzug — beides aus derselben SVG-Datei, es gibt also nur eine Quelle. Für Browser ohne SVG-Favicon liegt eine `favicon.ico` (16/32/48) daneben.

**Bugfix: drei Icons wurden als Text ausgeschrieben**
Die App lädt die klassische *Material Icons*-Schrift. Diese kennt weder `sentiment_extremely_dissatisfied` (Verängstigen-Button) noch `skull` (Gegner-Überschrift, Besiegt-Markierung, „ist bewusstlos"-Banner) noch `shield_outlined` (Konten, Kampfliste). Bei einem unbekannten Namen schreibt `mat-icon` das Wort **im Klartext** in den Button — es füllte ihn komplett aus und schob die Beschriftung aus dem sichtbaren Bereich. Genau deshalb wirkte der Verängstigen-Button „ohne Label". Ersetzt durch gültige Icons; ein neues Prüfskript (`frontend/scripts/check-mat-icons.py`) schlägt bei unbekannten Namen an — der Build bemerkt so etwas nicht.

**Icons für die Ansage-Buttons**
*Neutral*, *Aggressiv*, *Defensiv*, *Waffe*, *Zauber* und *Ändern* hatten Emoji oder gar kein Icon — jetzt echte Icons wie alle übrigen Buttons.

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
