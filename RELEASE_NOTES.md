# Release Notes

## v1.3.0 (in Entwicklung)

**Prod-Hotfix: Boot-Schleife nach dem v1.2.0-Deploy**
Alt-Datenbanken (vor Flyway von Hibernate angelegt) tragen CHECK-Constraints auf Enum-Spalten, die nur die damaligen Werte erlauben. Der neue Wert `DODGE_STEP` (Nebelschild) verletzte `spell_definitions_modify_stat_check` — das Backend starb beim Start, Docker startete es endlos neu (Dauer-CPU). Flyway `V38` entfernt alle diese Alt-Constraints (die Flyway-Baseline definiert selbst keine; die Wertebereichs-Prüfung liegt in der Anwendung). Auf per Baseline erzeugten Datenbanken ist die Migration ein No-op.

## v1.2.0 (18.07.2026)

### 🗺 Hexfeld-Kampfkarte (optional)

Der Kampf kann jetzt räumlich auf einer Hexfeld-Karte ausgetragen werden — als **optionale Zusatzschicht**: Ohne Aktivierung bleibt alles exakt wie bisher. Aktivierung per Checkbox beim Anlegen der Session (Größe wählbar, Standard 24×16) oder per Button im Setup; der „Karte"-Knopf öffnet die Karte **in einem eigenen Fenster**, das sich live mit dem Kampfscreen synchronisiert.

- **Spielleiter platziert**: Helden, Monster sowie Wände, Türen (zum Öffnen/Schließen), Bäume, Felsen und Möbel.
- **Bewegung in Initiative-Reihenfolge**: Jeder Kombattant läuft pro Runde bis zu seiner **Bewegungsrate** (neues Feld auf dem Charakterbogen, in Feldern). Eigenen Token anklicken → erreichbare Felder leuchten → Zielfeld anklicken. Hindernisse erzwingen Umwege (kürzester Weg zählt). Der Spielleiter darf jederzeit jeden versetzen.
- **Reichweiten steuern die Zielauswahl** im Kampfscreen: Nahkampf nur gegen angrenzende Felder, Projektil-/Wurfwaffen nach ihrer neuen **Kurz/Mittel/Weit-Reichweite**, Zauber nach ihrer neuen **Zauberreichweite**. Die Reichweiten filtern nur die Auswahl — die Kampfmechanik selbst bleibt unangetastet.
- **Animationen**: Nahkampfhiebe, fliegende Pfeile und Zauberbolzen werden auf der Karte animiert, sobald im Kampf gewürfelt wird — bei allen Zuschauern.

### 🧵 Zusätzliche Fäden bei Zaubern

Sind alle Pflichtfäden eines Zaubers gewoben, kann jeder weitere Faden eine der Zusatz-Optionen des Zaubers kaufen — auswählbar im Fadenweben-Dialog. Obergrenze: **Fadenweben-Rang**; dieselbe Option darf mehrfach gewählt werden. Auch **Sofortzauber ohne Pflichtfäden** (z.B. Blitz) können Zusatzfäden aufnehmen.

- Automatisch verrechnet werden **Wirkungsstufe** (+2 Schaden/Heilung), **Wirkungs-Verstärkung** (Buff-/Debuff-Modifikator) und **Wirkungsdauer** — je nach Zauber. Optionen wie Reichweite oder zusätzliche Ziele werden gewählt, protokolliert und angezeigt; die Auslegung liegt beim Spielleiter.
- **Freier Zusatzfaden aus der Erweiterten Matrize**: Ein Sofortzauber in einer Erweiterten Matrize erhält beim Wirken automatisch einen Gratis-Faden „Wirkungsstufe +2" — ohne Wurf, ohne Aktion, zählt nicht gegen die Obergrenze.
- Das Ergebnisfenster schlüsselt die Schadensstufe sauber auf: `Step 12 (6 + 4 Übererfolge + 2 Zusatzfäden)`.
- Optionen hinterlegt für 10 Illusionisten-Zauber (Katastrophe, Umhang, Vertrauen, Blitz, Illusionärer Blitz, Blindheit, Gedankennebel, Sehen von Verborgenem, Niemand Da, Phantomkrieger) sowie die vier Geisterbeschwörer-Zauber unten.

### ✨ Zauber regelgetreu mechanisiert

**Phantomkrieger**: Übererfolge verlängern die Dauer um **2 Runden je**; der Zusatzfaden „+1 Bild" gibt je Bild **+1 KV und −1 auf Angriffe gegen das Ziel** (aus +3/−3 wird mit zwei Bildern +5/−5).

**Blindheit**: jetzt **−4 auf alle Proben** (vorher −3 nur auf Angriffe). Übererfolge und der Zusatzfaden „+2 Minuten" verlängern um je **2 Minuten (20 Kampfrunden)**. **Durchschauen**: Würfelt das Opfer bei irgendeiner Aktionsprobe **über 17** — trotz des Malus —, endet der Effekt sofort (mit Protokolleintrag).

**Geisterpfeil**: senkt zusätzlich zum Schaden die **Mystische Rüstung des Ziels um 2** (2 Runden, +2 je Übererfolg). Zusatzfäden: Wirkungsstufe +2 und/oder MR um weitere 2 senken.

**Nebelschild**: Der Bonus wirkt regelgetreu **nur auf Ausweichen-Proben** (vorher pauschal +4 KV). Zusatzfaden: Bonus +2; Übererfolge verlängern die Dauer.

**Schmerzen**: 3 temporäre Wunden als **−3 auf alle Proben** plus **halbierte Bewegungsrate auf der Kampfkarte**. Zusatzfaden: +1 Wunde; Übererfolge verlängern die Dauer.

**Schädel des Todes**: endlich ein echter Effekt — **Verängstigen kostet keine Hauptaktion**, solange der Schädel aktiv ist (Spruchzauberei-Rang + 5 Runden, +2 je Übererfolg). Zusatzfaden: +2 auf Verängstigen-Proben.

### 🪄 Neue Talente

**Magie neutralisieren**: Beendet einen beliebigen aktiven Effekt (WIL + Rang vs. **Effektstufe + 10**, Aktion + 1 Überanstrengung). Auswahldialog und Ergebnis erscheinen **bei allen Mitspielern**; die Effektstufe wird im Dialog eingetragen, der Spielleiter entscheidet, was neutralisierbar ist.

**Verängstigen**: WIL + Rang vs. Mystische Verteidigung — bei Erfolg **−2 auf alle Aktionsproben je Erfolg** für Rang Runden. Das Ziel darf jede Runde eine Willenskraftprobe zum „Furcht abschütteln" ablegen. Geisterbeschwörer (1. Kreis), Illusionisten-Talentoption (Kreis 5–8).

### 🎨 Oberfläche

- **Eigenes App-Icon**: Fadenring mit Schwert — im Browser-Tab und als Logo in der Seitenleiste (eigener Entwurf, nicht das FASA-Logo).
- **Kombattanten-Kachel zweispaltig**: Kopfzeile über volle Breite, links die Werte, rechts alle Aktionsbuttons untereinander — **jeder Button jetzt mit Beschriftung und Icon**.
- **Ansage-Buttons** (Neutral/Aggressiv/Defensiv/Waffe/Zauber) mit echten Icons statt Emoji.

### 🐛 Bugfixes

- **Verängstigen verbraucht jetzt die Hauptaktion** (verbrauchte bisher gar keine, obwohl als Standardaktion dokumentiert). Mit Schädel des Todes entfällt der Verbrauch.
- **Kampfprotokoll war falsch herum** — die neuesten Einträge stehen jetzt oben (doppelte Umkehr zwischen Backend und Frontend).
- **Drei Icons wurden als Text ausgeschrieben** (Verängstigen-Button, Gegner-Schädel, Schild-Umriss): unbekannte Namen in der Material-Icons-Schrift. Ersetzt; ein Prüfskript (`frontend/scripts/check-mat-icons.py`) verhindert Wiederholung.
- **Ausrüstungsformulare** quetschten Name/Beschreibung auf Splitterbreite — Felder brechen jetzt um.
- **Browser-Freeze** beim Öffnen des Magie-neutralisieren-Dialogs behoben (Endlosschleife der Änderungserkennung).
- **Schwimmen-Talent** wurde beim Backend-Start fälschlich gelöscht — bleibt jetzt erhalten.

### ⚕️ Arzt-Umbau

Zwei Behandlungsmodi: **Verletzungen behandeln** (1× pro Erholungsprobe, +Rang auf den Wurf) und **Wunde versorgen** (unterdrückt den −1-Wundmalus je versorgter Wunde, mehrfach anwendbar). Beide würfeln WAH + Rang vs. MW 5 und verbrauchen je 1× Verbandszeug — auch bei Fehlschlag.

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
