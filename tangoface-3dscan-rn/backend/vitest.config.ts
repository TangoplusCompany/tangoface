import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  test: {
    include: [
      'tests/stateMachine.test.ts',
      'tests/pairing.test.ts',
      'tests/rateLimiter.test.ts',
      'tests/webhook.test.ts',
      'tests/workerLogic.test.ts',
      'tests/workerPull.test.ts',
    ],
  },
});
