/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      /* ========================================================================
         SmartHire visual system — "Paper & signal".
         Cool paper surfaces, ink typography, cobalt actions, a lime highlight.
         Legacy token names stay intact so every screen inherits the system.
         ======================================================================== */
      colors: {
        /* ---------- Neutral surfaces ---------- */
        'c-bg': '#F7F8FA',
        'c-surface': '#FFFFFF',
        'c-surface-sunken': '#F2F4F7',
        'c-surface-raised': '#FFFFFF',
        'c-border': '#E4E7EC',
        'c-border-strong': '#D0D5DD',
        'c-text': '#0B0D12',
        'c-text-muted': '#5B6472',
        'c-text-subtle': '#667085',
        'c-overlay': 'rgba(11, 13, 18, 0.46)',
        'c-scrim': 'rgba(11, 13, 18, 0.28)',

        /* on-surface aliases */
        'on-surface': '#0B0D12',
        'on-surface-variant': '#5B6472',

        /* ---------- Primary (cobalt) ---------- */
        'c-primary-50': '#EFF4FF',
        'c-primary-100': '#DBE6FF',
        'c-primary-200': '#C7D7FE',
        'c-primary-300': '#A4BDFF',
        'c-primary-400': '#7A9BFF',
        'c-primary-500': '#4D77FF',
        'c-primary-600': '#2457FF',
        'c-primary-700': '#1B45D6',
        'c-primary-800': '#1738A8',
        'c-primary-900': '#122B7A',
        'c-primary-950': '#0B1B4D',

        'on-primary': '#FFFFFF',

        /* ---------- Accent (signal lime) ---------- */
        'c-accent-50': '#F7FFE1',
        'c-accent-100': '#EEFFC2',
        'c-accent-200': '#E2FF9A',
        'c-accent-300': '#D8FF4F',
        'c-accent-400': '#C6F53A',
        'c-accent-500': '#B2E62A',
        'c-accent-600': '#93C51E',
        'c-accent-700': '#6F9A16',
        'c-accent-800': '#4E6D10',

        /* ---------- Semantic: success ---------- */
        'success-50': '#ECFDF3',
        'success-100': '#D1FADF',
        'success-200': '#A6F4C5',
        'success-500': '#12B76A',
        'success-600': '#039855',
        'success-700': '#027A48',
        'success-800': '#05603A',

        'on-success': '#FFFFFF',

        /* ---------- Semantic: warning ---------- */
        'warning-50': '#FFFAEB',
        'warning-100': '#FEF0C7',
        'warning-200': '#FEDF89',
        'warning-500': '#F79009',
        'warning-600': '#DC6803',
        'warning-700': '#B54708',
        'warning-800': '#93370D',

        'on-warning': '#FFFFFF',

        /* ---------- Semantic: danger ---------- */
        'danger-50': '#FEF3F2',
        'danger-100': '#FEE4E2',
        'danger-200': '#FECDCA',
        'danger-500': '#F04438',
        'danger-600': '#D92D20',
        'danger-700': '#B42318',
        'danger-800': '#912018',

        'on-danger': '#FFFFFF',

        /* ---------- Semantic: info ---------- */
        'info-50': '#EFF8FF',
        'info-100': '#D1E9FF',
        'info-200': '#B2DDFF',
        'info-500': '#2E90FA',
        'info-600': '#1570EF',
        'info-700': '#175CD3',
        'info-800': '#1849A9',

        'on-info': '#FFFFFF',

        /* ---------- Natural aliases ---------- */
        surface: '#FFFFFF',
        'surface-sunken': '#F2F4F7',
        background: '#F7F8FA',
        border: '#E4E7EC',
        primary: '#0B0D12',
        secondary: '#2457FF',
      },

      /* ---------- Typography ---------- */
      fontFamily: {
        sans: ['Instrument Sans', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'Helvetica', 'Arial', 'sans-serif'],
        display: ['Bricolage Grotesque', 'Instrument Sans', 'system-ui', 'sans-serif'],
        serif: ['Newsreader', 'Georgia', 'Cambria', 'Times New Roman', 'serif'],
        mono: ['IBM Plex Mono', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
      fontSize: {
        'display-xl': ['76px', { lineHeight: '0.96', fontWeight: '600', letterSpacing: '-0.05em' }],
        display: ['48px', { lineHeight: '1.02', fontWeight: '600', letterSpacing: '-0.04em' }],
        'headline-lg': ['32px', { lineHeight: '1.18', fontWeight: '600', letterSpacing: '-0.03em' }],
        'headline-md': ['24px', { lineHeight: '1.28', fontWeight: '600', letterSpacing: '-0.02em' }],
        'headline-sm': ['20px', { lineHeight: '1.35', fontWeight: '600', letterSpacing: '-0.015em' }],
        'body-lg': ['18px', { lineHeight: '30px', fontWeight: '400' }],
        'body-md': ['15px', { lineHeight: '24px', fontWeight: '400' }],
        'body-sm': ['14px', { lineHeight: '21px', fontWeight: '400' }],
        'body-strong': ['15px', { lineHeight: '24px', fontWeight: '600' }],
        'label-md': ['13px', { lineHeight: '18px', fontWeight: '600', letterSpacing: '0.005em' }],
        caption: ['12px', { lineHeight: '17px', fontWeight: '400', letterSpacing: '0.005em' }],
        overline: ['11px', { lineHeight: '16px', fontWeight: '600', letterSpacing: '0.08em' }],
        'code-sm': ['12px', { lineHeight: '17px', fontWeight: '400' }],
      },
      letterSpacing: {
        tightest: '-0.05em',
      },

      /* ---------- Spacing ---------- */
      spacing: {
        'space-xs': '0.25rem',
        'space-sm': '0.5rem',
        'space-md': '0.75rem',
        'space-lg': '1rem',
        'space-xl': '1.5rem',
        'space-2xl': '2rem',
        'space-3xl': '3rem',
        gutter: '1.75rem',
        'gutter-mobile': '1rem',
        topbar: '4.5rem',
        sidebar: '16.5rem',
        'sidebar-rail': '5rem',
      },

      maxWidth: {
        content: '84rem',
        prose: '68ch',
        narrow: '48rem',
      },

      borderRadius: {
        sm: '0.25rem',
        DEFAULT: '0.5rem',
        md: '0.625rem',
        lg: '0.75rem',
        xl: '1rem',
        '2xl': '1.25rem',
        '3xl': '1.5rem',
        full: '9999px',
      },

      boxShadow: {
        resting: '0 1px 2px rgba(16, 24, 40, 0.04), 0 1px 3px rgba(16, 24, 40, 0.06)',
        hover: '0 4px 8px -2px rgba(16, 24, 40, 0.08), 0 12px 24px -8px rgba(16, 24, 40, 0.10)',
        raised: '0 12px 32px -12px rgba(16, 24, 40, 0.18)',
        modal: '0 24px 64px -12px rgba(16, 24, 40, 0.24)',
        inset: 'inset 0 1px 0 rgba(255, 255, 255, 0.16)',
      },

      transitionDuration: {
        fast: '140ms',
        normal: '220ms',
        slow: '360ms',
      },

      zIndex: {
        sticky: '10',
        dropdown: '20',
        drawer: '30',
        scrim: '40',
        dialog: '50',
        toast: '60',
      },

      keyframes: {
        'fade-in': {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        'zoom-in': {
          from: { opacity: '0', transform: 'scale(0.97)' },
          to: { opacity: '1', transform: 'scale(1)' },
        },
        'slide-in-right': {
          from: { opacity: '0', transform: 'translateX(1rem)' },
          to: { opacity: '1', transform: 'translateX(0)' },
        },
        'slide-in-left': {
          from: { opacity: '0', transform: 'translateX(-100%)' },
          to: { opacity: '1', transform: 'translateX(0)' },
        },
        'slide-in-up': {
          from: { opacity: '0', transform: 'translateY(1rem)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        rise: {
          from: { opacity: '0', transform: 'translateY(18px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        marquee: {
          from: { transform: 'translateX(0)' },
          to: { transform: 'translateX(-50%)' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-8px)' },
        },
        'pulse-soft': {
          '0%, 100%': { opacity: '0.55' },
          '50%': { opacity: '1' },
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' },
        },
      },
      animation: {
        'fade-in': 'fade-in 220ms cubic-bezier(.2,0,.2,1) forwards',
        'zoom-in': 'zoom-in 220ms cubic-bezier(.2,0,.2,1) forwards',
        'slide-in-right': 'slide-in-right 220ms cubic-bezier(.2,0,.2,1) forwards',
        'slide-in-left': 'slide-in-left 280ms cubic-bezier(.2,0,.2,1) forwards',
        'slide-in-up': 'slide-in-up 280ms cubic-bezier(.2,0,.2,1) forwards',
        rise: 'rise 620ms cubic-bezier(.16,1,.3,1) both',
        marquee: 'marquee 32s linear infinite',
        float: 'float 6s ease-in-out infinite',
        'pulse-soft': 'pulse-soft 2.8s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};
