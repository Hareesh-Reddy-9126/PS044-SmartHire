/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      /* ========================================================================
         SmartHire visual system — "Midnight spectrum".
         A deep-space glass interface: indigo ink, aurora violet, signal mint.
         Every legacy token name is preserved so existing screens inherit the
         new identity without a single behavioural change.
         ======================================================================== */
      colors: {
        /* ---------- Neutral surfaces ---------- */
        'c-bg': '#070A18',
        'c-surface': '#0D1326',
        'c-surface-sunken': '#090E1E',
        'c-surface-raised': '#121A33',
        'c-border': '#1E2745',
        'c-border-strong': '#2C3860',
        'c-text': '#EDF1FF',
        'c-text-muted': '#9AA6C7',
        'c-text-subtle': '#7C89AC',
        'c-overlay': '#04060F',
        'c-scrim': '#04060F',

        /* on-surface aliases */
        'on-surface': '#EDF1FF',
        'on-surface-variant': '#9AA6C7',

        /* ---------- Primary (aurora violet) ---------- */
        'c-primary-50': '#171233',
        'c-primary-100': '#211A4D',
        'c-primary-200': '#3B2E7A',
        'c-primary-300': '#A78BFA',
        'c-primary-400': '#B9A7FF',
        'c-primary-500': '#8B5CF6',
        'c-primary-600': '#7C5CFF',
        'c-primary-700': '#C4B5FD',
        'c-primary-800': '#DDD6FE',
        'c-primary-900': '#EDE9FE',
        'c-primary-950': '#F5F3FF',

        'on-primary': '#FFFFFF',

        /* ---------- Accent (signal mint) ---------- */
        'c-accent-50': '#0A2429',
        'c-accent-100': '#0F3438',
        'c-accent-200': '#155E5A',
        'c-accent-300': '#5EEAD4',
        'c-accent-400': '#2DD4BF',
        'c-accent-500': '#3EE8D0',
        'c-accent-600': '#2DD4BF',
        'c-accent-700': '#99F6E4',
        'c-accent-800': '#CCFBF1',

        /* ---------- Semantic: success ---------- */
        'success-50': '#0A201B',
        'success-100': '#0E2E26',
        'success-200': '#1F5C4A',
        'success-500': '#34D399',
        'success-600': '#10B981',
        'success-700': '#6EE7B7',
        'success-800': '#A7F3D0',

        'on-success': '#04120D',

        /* ---------- Semantic: warning ---------- */
        'warning-50': '#231A08',
        'warning-100': '#33260B',
        'warning-200': '#6B4E12',
        'warning-500': '#FBBF24',
        'warning-600': '#F59E0B',
        'warning-700': '#FCD34D',
        'warning-800': '#FDE68A',

        'on-warning': '#1A1203',

        /* ---------- Semantic: danger ---------- */
        'danger-50': '#25101A',
        'danger-100': '#351522',
        'danger-200': '#6E2438',
        'danger-500': '#FB7185',
        'danger-600': '#F43F5E',
        'danger-700': '#FDA4AF',
        'danger-800': '#FECDD3',

        'on-danger': '#FFFFFF',

        /* ---------- Semantic: info ---------- */
        'info-50': '#08202E',
        'info-100': '#0B2B3D',
        'info-200': '#155A78',
        'info-500': '#38BDF8',
        'info-600': '#0EA5E9',
        'info-700': '#7DD3FC',
        'info-800': '#BAE6FD',

        'on-info': '#04121A',

        /* ---------- Natural aliases ---------- */
        surface: '#0D1326',
        'surface-sunken': '#090E1E',
        background: '#070A18',
        border: '#1E2745',
        primary: '#7C5CFF',
        secondary: '#3EE8D0',
      },

      /* ---------- Typography ---------- */
      fontFamily: {
        sans: ['Instrument Sans', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'Helvetica', 'Arial', 'sans-serif'],
        serif: ['Newsreader', 'Georgia', 'Cambria', 'Times New Roman', 'serif'],
        mono: ['IBM Plex Mono', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
      fontSize: {
        'display-xl': ['72px', { lineHeight: '68px', fontWeight: '300', letterSpacing: '-0.035em' }],
        display: ['44px', { lineHeight: '48px', fontWeight: '400', letterSpacing: '-0.03em' }],
        'headline-lg': ['30px', { lineHeight: '38px', fontWeight: '500', letterSpacing: '-0.02em' }],
        'headline-md': ['23px', { lineHeight: '31px', fontWeight: '500', letterSpacing: '-0.015em' }],
        'headline-sm': ['19px', { lineHeight: '27px', fontWeight: '600', letterSpacing: '-0.01em' }],
        'body-lg': ['18px', { lineHeight: '29px', fontWeight: '400' }],
        'body-md': ['15px', { lineHeight: '24px', fontWeight: '400' }],
        'body-sm': ['14px', { lineHeight: '21px', fontWeight: '400' }],
        'body-strong': ['15px', { lineHeight: '24px', fontWeight: '600' }],
        'label-md': ['13px', { lineHeight: '18px', fontWeight: '600', letterSpacing: '0.005em' }],
        caption: ['12px', { lineHeight: '17px', fontWeight: '400', letterSpacing: '0.005em' }],
        overline: ['11px', { lineHeight: '16px', fontWeight: '600', letterSpacing: '0.08em' }],
        'code-sm': ['12px', { lineHeight: '17px', fontWeight: '400' }],
      },
      letterSpacing: {
        tightest: '-0.045em',
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
        content: '86rem',
        prose: '68ch',
        narrow: '48rem',
      },

      borderRadius: {
        sm: '0.25rem',
        DEFAULT: '0.375rem',
        md: '0.5rem',
        lg: '0.625rem',
        xl: '0.875rem',
        '2xl': '1.25rem',
        full: '9999px',
      },

      boxShadow: {
        resting:
          'inset 0 1px 0 rgba(255, 255, 255, 0.045), 0 22px 55px -36px rgba(0, 0, 0, 0.92)',
        hover:
          'inset 0 1px 0 rgba(255, 255, 255, 0.07), 0 32px 70px -38px rgba(0, 0, 0, 0.96), 0 0 0 1px rgba(124, 92, 255, 0.16)',
        raised:
          'inset 0 1px 0 rgba(255, 255, 255, 0.07), 0 30px 80px -30px rgba(0, 0, 0, 0.9)',
        modal:
          'inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 50px 120px -30px rgba(0, 0, 0, 0.95), 0 0 0 1px rgba(124, 92, 255, 0.2)',
        inset: 'inset 0 1px 0 rgba(255, 255, 255, 0.16)',
        glow: '0 0 0 1px rgba(124, 92, 255, 0.28), 0 18px 55px -18px rgba(124, 92, 255, 0.55)',
        mint: '0 0 0 1px rgba(62, 232, 208, 0.24), 0 18px 55px -18px rgba(62, 232, 208, 0.4)',
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
        shimmer: {
          '100%': { transform: 'translateX(100%)' },
        },
        aurora: {
          '0%, 100%': { transform: 'translate3d(0, 0, 0) scale(1)', opacity: '0.75' },
          '33%': { transform: 'translate3d(4%, -3%, 0) scale(1.08)', opacity: '0.95' },
          '66%': { transform: 'translate3d(-3%, 4%, 0) scale(0.96)', opacity: '0.65' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-12px)' },
        },
        'float-soft': {
          '0%, 100%': { transform: 'translateY(0) rotate(0deg)' },
          '50%': { transform: 'translateY(-7px) rotate(0.6deg)' },
        },
        'spin-slow': {
          to: { transform: 'rotate(360deg)' },
        },
        'pulse-soft': {
          '0%, 100%': { opacity: '0.45', transform: 'scale(1)' },
          '50%': { opacity: '1', transform: 'scale(1.35)' },
        },
        'gradient-pan': {
          '0%, 100%': { backgroundPosition: '0% 50%' },
          '50%': { backgroundPosition: '100% 50%' },
        },
        'reveal-up': {
          from: { opacity: '0', transform: 'translateY(26px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'page-enter': {
          from: { opacity: '0', transform: 'translateY(10px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        'fade-in': 'fade-in 220ms cubic-bezier(.2,0,.2,1) forwards',
        'zoom-in': 'zoom-in 220ms cubic-bezier(.2,0,.2,1) forwards',
        'slide-in-right': 'slide-in-right 220ms cubic-bezier(.2,0,.2,1) forwards',
        'slide-in-left': 'slide-in-left 280ms cubic-bezier(.2,0,.2,1) forwards',
        'slide-in-up': 'slide-in-up 280ms cubic-bezier(.2,0,.2,1) forwards',
        rise: 'rise 620ms cubic-bezier(.16,1,.3,1) both',
        aurora: 'aurora 18s ease-in-out infinite',
        'aurora-slow': 'aurora 26s ease-in-out infinite reverse',
        float: 'float 7s ease-in-out infinite',
        'float-soft': 'float-soft 6s ease-in-out infinite',
        'spin-slow': 'spin-slow 26s linear infinite',
        'pulse-soft': 'pulse-soft 2.8s ease-in-out infinite',
        'gradient-pan': 'gradient-pan 8s ease infinite',
        'reveal-up': 'reveal-up 760ms cubic-bezier(.16,1,.3,1) both',
        'page-enter': 'page-enter 480ms cubic-bezier(.16,1,.3,1) both',
      },
    },
  },
  plugins: [],
};
