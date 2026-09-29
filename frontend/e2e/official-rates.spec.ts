import { test, expect } from '@playwright/test';
import { letters, routes } from './reference-rates';

// 9 routes × 10 physical letters × 2 printing sides × 2 color settings = 360 browser journeys.
// No intercepted responses: each journey uses the real React UI and packaged Java backend.
for (const route of routes) for (const letter of letters) for (const duplex of [false, true]) for (const color of [false, true]) {
  test(`${route.origin}→${route.destination} ${route.id} ${letter.sheets} sheets ${duplex ? 'duplex' : 'simplex'} ${color ? 'color' : 'mono'}`, async ({ page }) => {
    await page.goto('/');
    await page.getByRole('combobox', { name: 'Pays de départ', exact: true }).selectOption(route.origin);
    await page.getByRole('combobox', { name: 'Pays de destination', exact: true }).selectOption(route.destination);
    await page.getByRole('checkbox', { name: 'Impression recto verso', exact: true }).setChecked(duplex);
    await page.getByRole('checkbox', { name: 'Impression couleur', exact: true }).setChecked(color);
    await page.getByRole('checkbox', { name: 'Exiger le suivi du courrier', exact: true }).setChecked(route.tracked);
    await page.getByRole('spinbutton').fill(String(letter.sheets));
    await expect(page.getByRole('spinbutton')).toHaveAccessibleName(`Nombre de feuilles A4 (≈ ${letter.grams} g avec enveloppe)`);
    const responsePromise = page.waitForResponse(r => r.url().endsWith('/api/quotes') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Comparer les solutions', exact: true }).click();
    const response = await responsePromise;
    expect(response.status()).toBe(200);
    expect(response.request().postDataJSON()).toEqual({ origin: route.origin, destination: route.destination,
      pages: letter.sheets * (duplex ? 2 : 1), weight: letter.grams, color, duplex, tracking: route.tracked });
    const data = await response.json();
    expect(data.mode).toBe('PUBLIC_RATES');
    const quote = data.quotes.find((q: { id: string }) => q.id === route.id);
    expect(quote, 'Official postal offer must be present').toBeTruthy();
    const bracket = route.reference.rates.find(([limit]) => letter.grams <= limit);
    expect(bracket, 'Reference must cover this weight').toBeTruthy();
    expect(quote.originalPrice, route.reference.url).toBe(bracket![1]);
    expect(quote.originalCurrency).toBe(route.reference.currency);
    expect(quote.tracking).toBe(route.tracked);
    if (route.tracked) expect(data.quotes.every((q: { tracking: boolean }) => q.tracking)).toBe(true);
    // CAD conversion is the application's dated exchange-rate snapshot, not a current market rate.
    const expectedEuro = route.reference.currency === 'EUR' ? bracket![1] : Math.round(bracket![1] / 1.6047 * 100) / 100;
    expect(quote.price).toBe(expectedEuro);
    const card = page.locator('article.quote-card').filter({ has: page.locator('.quote-name p').filter({ hasText: `${quote.service} · À déposer vous-même` }) });
    await expect(card).toHaveCount(1);
    const displayMoney = (amount: number, currency: string) => new Intl.NumberFormat('fr-FR', { style: 'currency', currency }).format(amount);
    await expect(card.locator('.price strong')).toHaveText(displayMoney(expectedEuro, 'EUR'));
    if (route.reference.currency === 'CAD') await expect(card.locator('.price span')).toContainText(displayMoney(bracket![1], 'CAD'));
    await expect(card.locator('.price span')).toContainText(route.origin === 'CA' ? 'hors taxes' : 'TTC');
    await expect(card.getByRole('link', { name: /^Aller sur / })).toHaveAttribute('href', route.origin === 'CA'
      ? 'https://www.canadapost-postescanada.ca/cpc/fr/personnel.page' : 'https://www.laposte.fr/');
    await expect(card.getByRole('link', { name: /^Aller sur / })).toHaveAttribute('target', '_blank');
    await card.getByRole('button', { name: 'Détails', exact: true }).click();
    await expect(card).toContainText('Affranchissement seul');
    await expect(card.getByRole('link', { name: 'Consulter la source' })).toHaveAttribute('href', /^https:\/\//);
    await page.getByRole('button', { name: 'Dépôt postal', exact: true }).click();
    await expect(card).toBeVisible();
    await page.getByRole('combobox', { name: 'Trier les résultats' }).selectOption('price');
    const amounts = await page.locator('.quote-card .price strong').allTextContents();
    const parsed = amounts.map(value => Number(value.replace(/[^\d,]/g, '').replace(',', '.')));
    expect(parsed).toEqual([...parsed].sort((a,b) => a-b));
    await page.getByRole('checkbox', { name: 'Impression couleur', exact: true }).setChecked(!color);
    await expect(page.locator('.quote-card')).toHaveCount(0); // stale quotes disappear after edits.
  });
}
