# Release Notes

## v1.4.0 (in Entwicklung)

### ⚔️ Kampfregeln

**Korrektur: Karma auf Fernkampfschaden erst ab dem 5. Kreis**
Der Schütze durfte Karma bisher ab dem 1. Kreis auf Schadensproben mit Projektil- und Wurfwaffen einsetzen. Laut Spielerhandbuch ist das eine Fähigkeit des **5. Kreises** — wie beim Krieger im Nahkampf. Backend und Angriffsdialog prüfen den Kreis jetzt beide.

**Löwenherz**
Stärkt die mentale Entschlossenheit magisch. **Freie Aktion**, 1 Überanstrengung: bis zum Rundenende tritt die Löwenherzstufe **WIL + Rang** an die Stelle der normalen Willenskraftstufe, wenn eine Probe abgelegt wird, um die Wirkung von Talenten, Zaubern oder Fähigkeiten abzuschütteln. Greift damit beim **Abschütteln von Furcht**, bei der **Starrsinn-Gegenprobe gegen Verspotten** und beim **Eisernen Willen**. Erneutes Wirken ersetzt den Effekt, statt ihn zu stapeln.

**Sprint**
Steigert die Bewegungsrate magisch — **einfache Aktion**, also bleibt in derselben Runde noch eine Standardaktion (Angriff, Zauber) möglich. Kein Würfelwurf: die Bewegungsrate steigt für die aktuelle Runde um den Rang, 1 Überanstrengung. Wirkt direkt auf der **Hexfeld-Kampfkarte** — das Bewegungsbudget des Tokens wächst entsprechend. Auch als weltliche Fertigkeit (Kategorie Bewegung) nutzbar, nach denselben Regeln.

**Kobrastoß**
Steigert die Reaktionsgeschwindigkeit magisch. In der **Ansagephase** wird das Talent gegen einen bestimmten Gegner angesagt (2 Überanstrengung, einmal pro Runde). Die Initiative wird dann mit der Kobrastoßstufe **GES + Rang** statt der reinen GES-Stufe gewürfelt — Rüstungsmalus und andere Effekte bleiben erhalten. Anschließend wird das Ergebnis mit der Initiative des angesagten Gegners verglichen: **je Erfolg +2 auf die erste Angriffsprobe gegen genau diesen Gegner** in derselben Runde. Würfelt der Gegner höher, gibt es keinen Bonus — auch dann nicht, wenn er seine Handlung später verzögert und der Adept faktisch zuerst handelt.

Der Bonus erscheint im Angriffsergebnis als eigene Zeile und wird nur einmal verbraucht; Angriffe gegen andere Ziele lassen ihn unangetastet.

### 🪄 Neue Talente

**Sechs Geisterbeschwörer-Talente als Probentalente**
- **Geistersprache** (WAH) — versteht die Sprachen von Geistern und Astralwesen
- **Lesen/Schreiben** (WAH) — liest und verfasst geschriebene Texte
- **Lebensblick** (WAH) — nimmt Lebenskraft und Gesundheitszustand wahr
- **Stählerner Blick** (CHA) — schüchtert allein durch Augenkontakt ein
- **Nachtflieger Befehligen** (CHA) — befehligt fliegende Kreaturen der Nacht
- **Geisterreittier** (WIL) — beschwört ein Reittier aus Nebel und Feuer

Damit sind beim Geisterbeschwörer **22 von 28 Talenten** umgesetzt; offen bleibt der
geisterspezifische Teil (Beschwören, Verbannen, Fluch Unterdrücken, Astrale Interferenz,
Schadensverteilung, Hartnäckiges Gewebe).

**Drei Schützen-Talente als Probentalente**
- **Navigation** (WAH) — bestimmt Kurs und Position
- **Tieranalyse** (WAH) — erkennt Werte und Schwächen einer Kreatur
- **Beweisanalyse** (WAH) — untersucht physische Spuren

Damit sind beim Schützen **28 von 30 Talenten** umgesetzt; offen bleiben Weitschuss und
Bannmarkierung, die echte Regelmechanik brauchen.

**Sechs Diebes-Talente als Probentalente**
Aus der Diebes-Liste bis zum 5. Kreis:

- **Schloss Knacken** (GES) — öffnet mechanische Schlösser
- **Taschendiebstahl** (GES) — entwendet Gegenstände unbemerkt
- **Fallen Entschärfen** (GES) — macht mechanische und magische Fallen unschädlich
- **Feilschen** (CHA) — handelt Preise zum eigenen Vorteil aus
- **Weitsprung** (STÄ) — erhöht die Sprungdistanz magisch
- **Projektil Rufen** (WAH) — ruft abgefeuerte Munition in die Hand zurück

Damit sind beim Dieb **27 von 30 Talenten** umgesetzt; offen bleiben Überraschungsschlag,
Klingen Jonglieren und Untrüglicher Blick, die echte Regelmechanik brauchen.

**Zehn Illusionisten-Talente als Probentalente**
Aus der Illusionisten-Liste bis zum 5. Kreis, jeweils als Probe auf dem Charakterbogen und im Würfelwurf testbar:

- **Struktur Verstehen** (WAH) — versteht magische Schriften und lernt daraus neue Zauber
- **Astralsicht** (WAH) — nimmt den Astralraum und magische Muster wahr
- **Stimmen Imitieren** (WAH) — ahmt Stimmen und Geräusche nach
- **Konversation** (CHA) — hinterlässt im Gespräch einen vorteilhaften Eindruck
- **Magische Maske** (CHA) — magische Verkleidung als anderer Namensgeber
- **Arkanes Gefasel** (CHA) — beeindruckt mit magisch klingendem Kauderwelsch
- **Schuld Abwälzen** (CHA) — lenkt Verdacht glaubhaft auf jemand anderen
- **Totstellen** (WIL) — täuscht den eigenen Tod vor
- **Flinke Hand** (GES) — magische Taschenspielertricks
- **Gegenstand Verbergen** (GES) — versteckt Gegenstände am Körper

Damit sind vom Illusionisten bis Kreis 5 noch **vier Talente offen**, die echte Mechanik brauchen: Illusionsverstärkung, Untrüglicher Blick, Hartnäckiges Gewebe und Machtmaskierung.

**Acht Schwertmeister-Talente ohne Kampfrelevanz**
Damit ist die Schwertmeister-Talentliste bis auf die reinen Kampftalente vollständig. Alle acht sind als Probe auf dem Charakterbogen und im Würfelwurf testbar (Attributsstufe + Rang gegen Schwierigkeitswert bzw. Soziale Verteidigung):

- **Einschätzen** (WAH) — schätzt Kampfkraft, Rang oder Absichten eines Gegenübers ab
- **Beeindrucken** (STÄ) — Zurschaustellung von Kraft und Können
- **Gefahrensinn** (WAH) — spürt drohende Gefahr, bevor sie sichtbar wird
- **Gewinnendes Lächeln** (CHA) — entwaffnet mit charmantem Auftreten
- **Bleibender Eindruck** (CHA) — bleibt dauerhaft im Gedächtnis
- **Eleganter Abgang** (CHA) — verlässt eine Szene mit Stil
- **Wortgeplänkel** (CHA) — Schlagabtausch aus Spott und Witz
- **Luftgleiten** (GES) — gleitet kontrolliert durch die Luft und mildert Stürze

Damit ist die **komplette Schwertmeister-Talentliste umgesetzt**. *(Unempfindlichkeit ist bereits über die Bonus-Lebenspunkte pro Kreis der Disziplin abgebildet.)*

## v1.3.0 (16.08.2026)

### 🪄 Neue Talente

**Herzliches Lachen**
Stärkt mit befreiendem Lachen die Moral der Gefährten. **Einfache Aktion** — verbraucht keine Hauptaktion, der Adept kann in derselben Runde noch angreifen — und kostet 1 Überanstrengung. Probe: CHA + Rang gegen die höchste Soziale Verteidigung der Gegner. Bei Erfolg erhalten alle Verbündeten (inklusive Anwender) **+2 pro Erfolg auf ihre Soziale Verteidigung und auf Willenskraftproben zum Abschütteln von Furcht- und Einschüchterungseffekten**, für Rang Runden. Eigener Button und Ergebnisfenster im Kampf, bei allen Zuschauern synchronisiert.

**Fadenweben für jede Disziplin**
Bisher hatten nur die magischen Disziplinen ein Fadenweben-Talent. Jetzt bekommt jede nicht-magische Disziplin ein eigenes **Fadenweben (Disziplin)** — etwa Fadenweben (Krieger) oder Fadenweben (Dieb). Wahrnehmungsbasiert, als Probe testbar, ohne Kampfrelevanz. Steht automatisch in der Talentliste der jeweiligen Disziplin.

**Sechs Talente ohne Kampfrelevanz**
Erster Eindruck, Gefühlsmelodie, Etikette (Charisma) sowie Empathische Wahrnehmung, Forschen, Fremdsprachen (Wahrnehmung) — als Probe testbar, auf dem Charakterbogen und im Würfelwurf.

### ⚔️ Kampfregeln

**Karma auf Schadensproben je Disziplin**
Bisher ging Karma auf den Schadenswurf nur mit Krallenhand-Waffen. Jetzt zusätzlich disziplinabhängig: **Krieger** (Nahkampf, ab 5. Kreis), **Schütze** (Fernkampf), **Schwertmeister** (Nahkampfwaffe), **Luftpirat** (Nahkampf- oder Wurfwaffen), **Tiermeister** (waffenlos). Der Karma-auf-Schaden-Schalter im Angriffsdialog erscheint automatisch, wenn Disziplin und Waffe es erlauben. *(Die Größenbedingung des Luftpiraten wird mangels Waffengrößen-Daten nicht geprüft.)*

### 🎒 Ausrüstung

**Espagrastiefel** — **+1 auf Heimlicher Schritt und +2 auf Ausweichen** (Hieb ausweichen). Dafür können Gegenstände jetzt **zwei verschiedene Probenboni** tragen; beide erscheinen als Badge am Gegenstand und im Würfelwurf. Der Ausweichen-Bonus wirkt auch **im Kampf bei der Ausweichen-Reaktion**.

**Kletterausrüstung** — +4 auf Klettern-Proben.

**Anlege-Knöpfe verschwinden**, sobald der Charakter den jeweiligen Gegenstand besitzt — kein versehentliches Doppelanlegen mehr.

### 🐛 Bugfixes

**Prod-Hotfix: Boot-Schleife nach dem v1.2.0-Deploy**
Alt-Datenbanken (vor Flyway von Hibernate angelegt) tragen CHECK-Constraints auf Enum-Spalten, die nur die damaligen Werte erlauben. Der neue Wert `DODGE_STEP` (Nebelschild) verletzte diese Prüfung — das Backend starb beim Start und wurde endlos neu gestartet, was den Server dauerhaft auslastete. Flyway `V38` entfernt die Alt-Constraints; auf regulär angelegten Datenbanken ist die Migration wirkungslos. Ohne den Fix wären dieselben Fehler später auch im Kampf aufgetreten (Bewegungs-Logeinträge, Nebelschild- und Schmerzen-Effekte).

**Produktions-Build brach ab** — ein Typfehler im Angriffsdialog fiel erst im Produktions-Build auf und ließ den Docker-Build scheitern. Behoben; Frontend-Änderungen werden seitdem mit der Produktionskonfiguration geprüft.

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
