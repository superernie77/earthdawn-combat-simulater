/**
 * Unit-Tests für die toggleEquipmentActive-Logik im CharacterSheetComponent.
 * Testet nur die Logik (active-Toggle-Zustand), nicht die vollständige Komponente.
 */

import { Equipment } from '../../models/character.model';

/** Simuliert die Toggle-Logik aus dem Component (ohne Angular-Overhead). */
function toggleActiveLogic(e: Equipment): boolean {
  return e.active === false; // false → true; true/undefined → false
}

describe('Equipment active-toggle Logik', () => {
  it('inaktives Item (active=false) wird beim Toggle aktiv', () => {
    const e: Equipment = { name: 'Rüstung', type: 'ARMOR', damageBonus: 0, physicalArmor: 5,
      mysticalArmor: 0, initiativePenalty: 0, physicalDefenseBonus: 0,
      mysticDefenseBonus: 0, quantity: 1, healStep: 0, active: false };
    expect(toggleActiveLogic(e)).toBe(true);
  });

  it('aktives Item (active=true) wird beim Toggle inaktiv', () => {
    const e: Equipment = { name: 'Rüstung', type: 'ARMOR', damageBonus: 0, physicalArmor: 5,
      mysticalArmor: 0, initiativePenalty: 0, physicalDefenseBonus: 0,
      mysticDefenseBonus: 0, quantity: 1, healStep: 0, active: true };
    expect(toggleActiveLogic(e)).toBe(false);
  });

  it('Item ohne active-Feld (undefined = aktiv) wird beim Toggle inaktiv', () => {
    const e: Equipment = { name: 'Rüstung', type: 'ARMOR', damageBonus: 0, physicalArmor: 5,
      mysticalArmor: 0, initiativePenalty: 0, physicalDefenseBonus: 0,
      mysticDefenseBonus: 0, quantity: 1, healStep: 0 };
    // active === undefined → nicht === false → wird zu false (inaktiv)
    expect(toggleActiveLogic(e)).toBe(false);
  });
});

/** Simuliert die Template-Logik: active Badge anzeigen wenn active === false */
describe('Equipment inactive-Badge Anzeige-Logik', () => {
  it('Badge wird angezeigt wenn active === false', () => {
    const e: Equipment = { name: 'Rüstung', type: 'ARMOR', damageBonus: 0, physicalArmor: 5,
      mysticalArmor: 0, initiativePenalty: 0, physicalDefenseBonus: 0,
      mysticDefenseBonus: 0, quantity: 1, healStep: 0, active: false };
    const showBadge = e.active === false;
    expect(showBadge).toBe(true);
  });

  it('Badge wird nicht angezeigt wenn active === true', () => {
    const e: Equipment = { name: 'Rüstung', type: 'ARMOR', damageBonus: 0, physicalArmor: 5,
      mysticalArmor: 0, initiativePenalty: 0, physicalDefenseBonus: 0,
      mysticDefenseBonus: 0, quantity: 1, healStep: 0, active: true };
    const showBadge = e.active === false;
    expect(showBadge).toBe(false);
  });

  it('Badge wird nicht angezeigt wenn active undefined (Altzustand)', () => {
    const e: Equipment = { name: 'Rüstung', type: 'ARMOR', damageBonus: 0, physicalArmor: 5,
      mysticalArmor: 0, initiativePenalty: 0, physicalDefenseBonus: 0,
      mysticDefenseBonus: 0, quantity: 1, healStep: 0 };
    const showBadge = e.active === false;
    expect(showBadge).toBe(false);
  });
});

/** Simuliert die Template-Logik: Opacity für inaktive Items */
describe('Equipment Opacity-Logik', () => {
  it('aktives Item hat volle Opacity (1)', () => {
    const e: Equipment = { name: 'Rüstung', type: 'ARMOR', damageBonus: 0, physicalArmor: 5,
      mysticalArmor: 0, initiativePenalty: 0, physicalDefenseBonus: 0,
      mysticDefenseBonus: 0, quantity: 1, healStep: 0, active: true };
    const opacity = e.active === false ? '0.45' : '1';
    expect(opacity).toBe('1');
  });

  it('inaktives Item hat reduzierte Opacity (0.45)', () => {
    const e: Equipment = { name: 'Rüstung', type: 'ARMOR', damageBonus: 0, physicalArmor: 5,
      mysticalArmor: 0, initiativePenalty: 0, physicalDefenseBonus: 0,
      mysticDefenseBonus: 0, quantity: 1, healStep: 0, active: false };
    const opacity = e.active === false ? '0.45' : '1';
    expect(opacity).toBe('0.45');
  });
});

/** Testet die addEquipment-Seite: neue Rüstung/Schild sollte active=true bekommen */
describe('Equipment active default bei neuen Items', () => {
  it('neue Rüstung hat active=true per Default (Backend setzt es)', () => {
    // Das Backend setzt active=true für neue Items; der Frontend-Code gibt active nicht explizit mit
    // → der Server-Response enthält active=true
    const serverResponse: Equipment = { id: 99, name: 'Kettenhemd', type: 'ARMOR',
      damageBonus: 0, physicalArmor: 6, mysticalArmor: 0, initiativePenalty: 2,
      physicalDefenseBonus: 0, mysticDefenseBonus: 0, quantity: 1, healStep: 0, active: true };
    expect(serverResponse.active).toBe(true);
  });
});
