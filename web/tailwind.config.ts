import type { Config } from 'tailwindcss'

/**
 * ALIVE's design tokens, as the app defines them.
 *
 * The colour values are not repeated here on purpose: they arrive from
 * `app/tokens.css`, which `web/scripts/sprites.mjs` generates out of the
 * Android resources. A token written twice is a token that drifts, and the whole
 * point of the site is that it is the same object as the app.
 */
const config: Config = {
  content: ['./app/**/*.{ts,tsx}', './components/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        paper: 'var(--alive-paper)',
        canvas: 'var(--alive-canvas)',
        ink: 'var(--alive-ink)',
        'ink-secondary': 'var(--alive-ink_secondary)',
        'ink-muted': 'var(--alive-ink_muted)',
        'ink-hairline': 'var(--alive-ink_hairline)',
        white: 'var(--alive-white)',
        forest: 'var(--alive-forest)',
        'forest-soft': 'var(--alive-forest_soft)',
        'soft-forest': 'var(--alive-soft_forest)',
        'warm-gray': 'var(--alive-warm_gray)',
        'gold-dark': 'var(--alive-gold_reward_dark)',
        'gold-mid': 'var(--alive-gold_reward_mid)',
        'gold-pale': 'var(--alive-gold_reward_pale)',
        'red-dark': 'var(--alive-red_short_dark)',
        'red-mid': 'var(--alive-red_short_mid)',
        'red-pale': 'var(--alive-red_short_pale)',
        'blue-dark': 'var(--alive-blue_work_dark)',
        'blue-mid': 'var(--alive-blue_work_mid)',
        'blue-soft': 'var(--alive-blue_work_soft)',
        'blue-pale': 'var(--alive-blue_work_pale)',
        amber: 'var(--alive-amber_action)',
        pink: 'var(--alive-pink_soft)'
      },
      /** One radius per object size, the way the app's dimens say it. */
      borderRadius: {
        step: '4px',
        tile: '12px',
        field: '14px',
        card: '16px'
      },
      /** The picture every raised object wears: a bold edge and a hard offset shadow. */
      boxShadow: {
        raise: '6px 6px 0 0 var(--alive-ink)',
        'raise-sm': '4px 4px 0 0 var(--alive-ink)',
        'raise-lg': '8px 8px 0 0 var(--alive-ink)'
      },
      fontFamily: {
        display: ['var(--font-display)', 'monospace'],
        head: ['var(--font-head)', 'sans-serif'],
        body: ['var(--font-body)', 'sans-serif']
      },
      fontSize: {
        /** The pixel voice: numbers, wordmark, the one loud line on a panel. */
        pixel: ['clamp(2.5rem, 9vw, 5.5rem)', { lineHeight: '1', letterSpacing: '0' }],
        'pixel-sm': ['clamp(1.5rem, 4vw, 2.25rem)', { lineHeight: '1', letterSpacing: '0' }],
        'pixel-xs': ['1rem', { lineHeight: '1', letterSpacing: '0' }]
      }
    }
  },
  plugins: []
}

export default config
