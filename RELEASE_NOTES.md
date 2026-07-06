# Release Notes

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
