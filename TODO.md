# TODO

Offene Punkte aus der Arbeit an den Talentlisten. Alle reinen Probentalente sind umgesetzt —
was hier steht, braucht echte Regelmechanik und je einen Regeltext.

## Abdeckung der geprüften Disziplinen (bis Kreis 5)

| Disziplin | Stand |
|---|---|
| Schwertmeister | 31 / 31 ✅ |
| Schütze | 28 / 30 |
| Dieb | 27 / 30 |
| Illusionist | 25 / 29 |
| Geisterbeschwörer | 22 / 28 |

## Fehlende Talente (dedupliziert)

13 verschiedene Talente, 15 Nennungen — zwei werden von je zwei Disziplinen gebraucht.

### Von zwei Disziplinen gebraucht

- [ ] **Hartnäckiges Gewebe** — *Illusionist, Geisterbeschwörer*
  Erschwert das Neutralisieren eigener Zauber.
  Anknüpfung: `CombatService.performNeutralizeMagic(...)` — dort existiert bereits eine Probe,
  gegen die der Rang als Erschwernis gerechnet werden könnte.

- [ ] **Untrüglicher Blick** — *Illusionist, Dieb*
  Hilft, fremde Illusionen zu durchschauen.
  Anknüpfung: `CombatService.durchschauenCheck(session, roller, rollTotal)` — dort läuft heute
  die Blindheits-Probe (Aktionswurf > 17). Gegenstück zu Illusionsverstärkung; beide sollten
  zusammen entworfen werden.

### Illusionist

- [ ] **Illusionsverstärkung** (Kreis 1) — macht eigene Illusionen schwerer durchschaubar.
  Anknüpfung: Zauberverteidigung der eigenen Zaubereffekte bzw. der Mindestwurf, gegen den ein
  Betrachter das Durchschauen würfelt. Offen: wirkt der Rang additiv auf den Mindestwurf, und
  gilt er für alle eigenen Illusionen oder nur für frisch gewirkte?

- [ ] **Machtmaskierung** (Kreis 5) — verbirgt die eigene magische Aura vor Entdeckung.
  Es gibt bislang kein Konzept für Aura-Entdeckung. Braucht erst eine Entscheidung, wogegen die
  Probe überhaupt läuft (Astralsicht des Beobachters?).

### Dieb

- [ ] **Überraschungsschlag** (Novize) — Zusatzschaden gegen unvorbereitete Ziele.
  Anknüpfung: sehr nah an `Schwachstelle erkennen` (ziel-gebundener `ActiveEffect` mit
  `DAMAGE_STEP` und `ON_DAMAGE_DEALT`). Der Zustand „Toter Winkel" existiert bereits
  (`applyGmCondition`), auf „überrascht" ließe sich das erweitern. Offen: Höhe des Bonus und
  wie „unvorbereitet" bestimmt wird.

- [ ] **Klingen Jonglieren** (Geselle) — Barriere aus wirbelnden Messern zur Verteidigung.
  Vermutlich ein KV-Bonus über eine Dauer, also `PHYSICAL_DEFENSE` per `ActiveEffect`.
  Offen: Wurf, Kosten, Dauer, ob der Bonus je Erfolg steigt.

### Schütze

- [ ] **Weitschuss** (Kreis 4) — erhöht die Reichweite von Fernkampfwaffen.
  Anknüpfung: Waffen tragen seit der Hexkarte `rangeShort/rangeMedium/rangeLong`, und
  `filterByMapRange()` filtert die Zielauswahl danach. Offen: um wieviel je Rang, und ob mit
  Malus auf die Probe.

- [ ] **Bannmarkierung** (Geselle) — magisches Zeichen, das ein Ziel vor Furcht erstarren lässt.
  Anknüpfung: die Furcht-Mechanik ist vollständig vorhanden (`performFear`, `resistFear`,
  Effekt `Verängstigt` mit `resistTargetNumber`; Löwenherz und Herzliches Lachen wirken darauf).
  Offen: Wurf, Kosten, Dauer und ob „erstarrt" härter ist als der bestehende Furchtzustand.

### Geisterbeschwörer

**Zuerst zu klären:** Treten beschworene Geister als **eigene Kombattanten** in die Session ein,
oder bleiben sie ein Effekt auf dem Beschwörer? Davon hängen die ersten beiden Punkte und die
Blutbeschwörung ab.

- [ ] **Beschwören [Verbündetengeister]** (Kreis 5) — das größte Stück, siehe Frage oben.

- [ ] **Verbannen** (Geselle) — schickt Geister in ihre Heimatebene zurück. Setzt voraus, dass
  Geister überhaupt als Kombattanten existieren.

- [ ] **Fluch Unterdrücken** (Novize) — Widerstand gegen schädliche magische Effekte.
  Anknüpfung: liegt nah bei `performIronWill` (entfernt den jüngsten negativen SPELL-Effekt) und
  profitiert vermutlich von Löwenherz. Offen: Wurf gegen was, und welche Effekte erfasst werden.

- [ ] **Astrale Interferenz** (Geselle) — erschwert Magieanwendung in der Umgebung.
  Anknüpfung: `SpellService` würfelt Fadenweben und Spruchzauberei über den `ModifierAggregator`;
  ein Malus ließe sich als `ActiveEffect` auf die Betroffenen legen. Offen: Reichweite (die
  Hexkarte könnte den Radius liefern), Dauer, wer betroffen ist.

- [ ] **Schadensverteilung** (Geselle) — überträgt erlittenen Schaden auf andere Ziele.
  Anknüpfung: `applyDamageToDefender` ist die zentrale Stelle. Offen: wieviel wird übertragen,
  auf wieviele Ziele, und ob das Ziel widerstehen darf.

## Besondere Fähigkeiten (keine Talente)

- [ ] **Schattenmantel** (Dieb, Kreis 5). Regel bekannt: Einfache Aktion, 2 Überanstrengung,
  erhöht den Mindestwurf, den Dieb zu entdecken, um +2 für *Rang in Diebweben* Minuten. Es gibt
  im System aber keine Entdecken-Gegenprobe (Heimlicher Schritt ist reine Charakterbogen-Probe).
  Am ehesten als `ActiveEffect` mit Anzeige, den der Spielleiter bei Proben berücksichtigt.

- [ ] **Projektil Erschaffen** (Schütze, Kreis 5). Standardaktion, 1 Überanstrengung, erschafft
  Munition für *Rang in Pfeilweben* Minuten. Munition wird im System gar nicht getrackt —
  bräuchte erst eine Entscheidung, ob das abgebildet werden soll.

- [ ] **Blutbeschwörung** (Geisterbeschwörer, Kreis 5). Blutmagieschaden in Höhe der Geisterstärke
  für einen zusätzlichen Erfolg beim Beschwören. Blutmagieschaden existiert bereits
  (`bloodMagicDamage(...)` im `ModifierAggregator`). Hängt an „Beschwören".

## Karma-Einsätze je Disziplin

Vorhanden: Karma auf Initiative (Dieb, Kundschafter, Luftsegler, Schütze ab Kreis 3) und Karma
auf Schadensproben (Krieger/Schütze ab Kreis 5, Schwertmeister, Luftpirat, Tiermeister).
Muster dafür: `KARMA_INITIATIVE_DISCIPLINES` und `karmaOnDamageAllowed(...)`.

- [ ] **Dieb:** Karma auf CHA-Proben zum Täuschen (Kreis 1); Karma auf Angriffsproben gegen
  überraschte Gegner oder aus dem Toten Winkel (Kreis 5).
- [ ] **Schütze:** Karma auf sichtbasierte Wahrnehmungsproben (Kreis 1).
- [ ] **Geisterbeschwörer:** Karma auf Proben gegen Dämonen, Dämonenkonstrukte oder Untote
  (Kreis 3) — dafür fehlt ein Kreaturentyp-Konzept, Kombattanten kennen heute nur „NSC ja/nein".
  Und Karma, um den Malus eines eigenen Zaubers um 2 zu erhöhen (Kreis 5) — ließe sich an den
  Debuff-Zweig im `SpellService` hängen.

## Allgemeine Features

- [ ] **Automatische Kreis-Boni.** Mehrere Disziplinen bekommen feste Verteidigungsboni bei
  bestimmten Kreisen (Dieb: +1 KV auf 2, +1 Soziale VK auf 4; Schütze: +1 KV auf 2, +1 Mystische
  VK auf 4; Geisterbeschwörer: +1 Mystische VK auf 2, +1 Soziale VK auf 4). Heute nur manuell
  über die Verteidigungs-Boni am Charakterbogen. Wäre eine Bonus-Tabelle je Disziplin und Kreis.

- [ ] **Kostenlose Talente mit „Rang = Kreis".** Gefahrensinn (Dieb), Projektil Rufen (Schütze),
  die Standardmatrizen (Illusionist, Geisterbeschwörer) sind laut Regeln kostenlos und wachsen
  automatisch mit dem Kreis. Die Talente existieren, der Automatismus nicht.

## Kleinkram

- [ ] **Zugriffslisten der Disziplinen unvollständig.** `DataInitializer.migrateDisciplineAccessLists()`
  listet z.B. für den Schwertmeister nur fünf Kampftalente. Ohne funktionale Folgen —
  `accessTalentNames` wird im Frontend nirgends zum Filtern benutzt, alle Talente sind überall
  wählbar. Nur, falls die Liste mal Bedeutung bekommen soll.

- [ ] **`CLAUDE.md` ist an einer Stelle falsch.** Unter „Core Mechanics" steht
  *„Attributes = Steps, attribute value directly equals step number (1:1)"*. Das stimmt nicht mit
  `StepRollService.attributeToStep()` überein (z.B. WAH 15 → Stufe 6, nicht 15).

- [ ] **Attributwahl bei einigen Talenten prüfen.** Nicht eindeutig waren: **Stimmen Imitieren**
  (jetzt WAH), **Totstellen** (jetzt WIL), **Geisterreittier** (jetzt WIL), **Geistersprache**
  (jetzt WAH). Falls die Quelle etwas anderes sagt: je eine Zeile im Seed in
  `DataInitializer.migrateUtilityTalents()`.
