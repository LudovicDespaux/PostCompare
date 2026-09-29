import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: process.env.CI ? 2 : 4,
  timeout: 30000,
  reporter: [['list'], ['html', { open: 'never' }], ['json', { outputFile: 'test-results/results.json' }]],
  use: { baseURL: 'http://127.0.0.1:8083', browserName: 'chromium', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  webServer: {
    command: 'java -jar ../backend/target/postcompare-0.1.0.jar --server.address=127.0.0.1 --server.port=8083',
    url: 'http://127.0.0.1:8083/api/countries',
    reuseExistingServer: false,
    timeout: 60000,
  },
});
