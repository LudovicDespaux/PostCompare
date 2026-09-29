// Independent manual transcription of official pages consulted 2026-09-29.
// Do NOT derive these expectations from the application's tariffs.json or API.
export const references = {
  frDomestic: { url: 'https://www.laposte.fr/tarif-lettre-verte', currency: 'EUR', rates: [[20, 1.52], [100, 3.10], [250, 5.24]], checkedOn: '2026-09-29' },
  frInternational: { url: 'https://www.laposte.fr/tarifs-postaux-etranger', currency: 'EUR', rates: [[20, 2.25], [100, 4.85], [250, 11.65]], checkedOn: '2026-09-29' },
  frTracked: { url: 'https://www.laposte.fr/tarifs-postaux-etranger', currency: 'EUR', rates: [[20, 5.05], [100, 7.65], [250, 14.45]], checkedOn: '2026-09-29' },
  caDomestic: { url: 'https://www.canadapost-postescanada.ca/cpc/en/personal/stamp-prices.page', currency: 'CAD', rates: [[30, 1.44], [50, 1.75], [100, 2.61], [200, 4.29]], checkedOn: '2026-09-29' },
  caUs: { url: 'https://www.canadapost-postescanada.ca/cpc/en/personal/stamp-prices.page', currency: 'CAD', rates: [[30, 1.75], [50, 2.61], [100, 4.29], [200, 7.49]], checkedOn: '2026-09-29' },
  caInternational: { url: 'https://www.canadapost-postescanada.ca/cpc/en/personal/stamp-prices.page', currency: 'CAD', rates: [[30, 3.65], [50, 5.21], [100, 8.60], [200, 14.99]], checkedOn: '2026-09-29' },
} as const;

// Explicit examples exercise 20/30/50/100g boundaries and the envelope-size switch.
export const letters = [
  { sheets: 1, grams: 11 }, { sheets: 2, grams: 16 }, { sheets: 3, grams: 21 },
  { sheets: 4, grams: 26 }, { sheets: 5, grams: 31 }, { sheets: 6, grams: 45 },
  { sheets: 7, grams: 50 }, { sheets: 17, grams: 100 }, { sheets: 18, grams: 105 },
  { sheets: 25, grams: 140 },
];

export const routes = [
  { origin: 'FR', destination: 'FR', id: 'laposte-lettre-verte', reference: references.frDomestic, tracked: false },
  { origin: 'FR', destination: 'RO', id: 'laposte-internationale', reference: references.frInternational, tracked: false },
  { origin: 'FR', destination: 'US', id: 'laposte-internationale', reference: references.frInternational, tracked: false },
  { origin: 'FR', destination: 'JP', id: 'laposte-internationale', reference: references.frInternational, tracked: false },
  { origin: 'FR', destination: 'RO', id: 'laposte-suivie-internationale', reference: references.frTracked, tracked: true },
  { origin: 'CA', destination: 'CA', id: 'canadapost-poste-lettres', reference: references.caDomestic, tracked: false },
  { origin: 'CA', destination: 'US', id: 'canadapost-poste-lettres-international', reference: references.caUs, tracked: false },
  { origin: 'CA', destination: 'RO', id: 'canadapost-poste-lettres-international', reference: references.caInternational, tracked: false },
  { origin: 'CA', destination: 'JP', id: 'canadapost-poste-lettres-international', reference: references.caInternational, tracked: false },
];
