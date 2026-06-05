import { CharacterService } from './character.service';
import { of } from 'rxjs';

/**
 * Unit-Tests für CharacterService.setEquipmentActive.
 * Kein TestBed — HttpClient wird per Spy gemockt.
 */
describe('CharacterService — setEquipmentActive', () => {
  let service: CharacterService;
  let httpSpy: { patch: jest.Mock };

  beforeEach(() => {
    httpSpy = { patch: jest.fn() };
    service = new CharacterService(httpSpy as any);
  });

  it('ruft PATCH /api/characters/:id/equipment/:eqId/active?active=true auf', () => {
    const mockChar: any = { id: 1, name: 'Test', equipment: [] };
    httpSpy.patch.mockReturnValue(of(mockChar));

    service.setEquipmentActive(1, 10, true).subscribe();

    expect(httpSpy.patch).toHaveBeenCalledWith(
      '/api/characters/1/equipment/10/active',
      null,
      { params: { active: true } }
    );
  });

  it('ruft PATCH mit active=false auf', () => {
    const mockChar: any = { id: 1, name: 'Test', equipment: [] };
    httpSpy.patch.mockReturnValue(of(mockChar));

    service.setEquipmentActive(1, 20, false).subscribe();

    expect(httpSpy.patch).toHaveBeenCalledWith(
      '/api/characters/1/equipment/20/active',
      null,
      { params: { active: false } }
    );
  });

  it('gibt das Observable des aktualisierten Charakters zurück', (done) => {
    const mockChar: any = { id: 5, name: 'Kaelen', equipment: [{ id: 10, type: 'ARMOR', active: true }] };
    httpSpy.patch.mockReturnValue(of(mockChar));

    service.setEquipmentActive(5, 10, true).subscribe(result => {
      expect(result).toEqual(mockChar);
      done();
    });
  });

  it('konstruiert die URL korrekt mit verschiedenen IDs', () => {
    httpSpy.patch.mockReturnValue(of({}));
    service.setEquipmentActive(42, 99, true).subscribe();
    const [url] = httpSpy.patch.mock.calls[0];
    expect(url).toBe('/api/characters/42/equipment/99/active');
  });
});
