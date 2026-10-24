/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      /* ========================================================================
         SmartHire visual system — "The decision record".
         Deep evergreen ink, cool chalk paper, restrained brass.
         ======================================================================== */
      colors: {
        /* ---------- Neutral surfaces ---------- */
        'c-bg': '#F2F4EF',
        'c-surface': '#FFFFFF',
        'c-surface-sunken': '#E9EDE6',
        'c-surface-raised': '#FFFFFF',
        'c-border': '#DAE1D8',
        'c-border-strong': '#C0CBC1',
        'c-text': '#15231E',
        'c-text-muted': '#5A6A62',
        'c-text-subtle': '#5F6E65',
        'c-overlay': 'rgba(9, 28, 23, 0.58)',
        'c-scrim': 'rgba(9, 28, 23, 0.38)',

        /* on-surface aliases */
        'on-surface': '#15231E',
        'on-surface-variant': '#5A6A62',

        /* ---------- Primary (evergreen ink) ---------- */
        'c-primary-50': '#EDF3EF',
        'c-primary-100': '#D7E5DD',
        'c-primary-200': '#B7D0C4',
        'c-primary-300': '#8FAFA0',
        'c-primary-400': '#668C7B',
        'c-primary-500': '#456F5D',
        'c-primary-600': '#305947',
        'c-primary-700': '#244737',
        'c-primary-800': '#19372B',
        'c-primary-900': '#0F271F',
        'c-primary-950': '#091B15',

        'on-primary': '#FFFFFF',

        /* ---------- Accent (restrained brass) ---------- */
        'c-accent-50': '#F8F2E5',
        'c-accent-100': '#EFE1C6',
        'c-accent-200': '#E1CBA4',
        'c-accent-300': '#D5B782',
        'c-accent-400': '#C9A367',
        'c-accent-500': '#B78E4F',
        'c-accent-600': '#9A733D',
        'c-accent-700': '#7A582E',
        'c-accent-800': '#5B4122',

        /* ---------- Semantic: success ---------- */
        'success-50': '#EEF5EF',
        'success-100': '#D7E9DB',
        'success-200': '#B3D4BC',
        'success-500': '#3C7755',
        'success-600': '#2E6043',
        'success-700': '#234B34',
        'success-800': '#193826',

        'on-success': '#FFFFFF',

        /* ---------- Semantic: warning ---------- */
        'warning-50': '#FAF3E5',
        'warning-100': '#F2E4C8',
        'warning-200': '#E7CF9F',
        'warning-500': '#AD7A21',
        'warning-600': '#8C6118',
        'warning-700': '#6E4C13',
        'warning-800': '#52390F',

        'on-warning': '#FFFFFF',

        /* ---------- Semantic: danger ---------- */
        'danger-50': '#FAEDEB',
        'danger-100': '#F2D9D6',
        'danger-200': '#E5BDB7',
        'danger-500': '#B14B3D',
        'danger-600': '#923A2F',
        'danger-700': '#742E25',
        'danger-800': '#58231C',

        'on-danger': '#FFFFFF',

        /* ---------- Semantic: info ---------- */
        'info-50': '#EDF3F7',
        'info-100': '#D8E7EF',
        'info-200': '#B9D2E0',
        'info-500': '#497A97',
        'info-600': '#38627A',
        'info-700': '#2B4C5F',
        'info-800': '#203A48',

        'on-info': '#FFFFFF',

        /* ---------- Natural aliases ---------- */
        surface: '#FFFFFF',
        'surface-sunken': '#E9EDE6',
        background: '#F2F4EF',
        border: '#DAE1D8',
        primary: '#244737',
        secondary: '#B78E4F',
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
        resting: '0 1px 2px rgba(16, 39, 31, 0.05), 0 8px 24px -20px rgba(16, 39, 31, 0.22)',
        hover: '0 2px 4px rgba(16, 39, 31, 0.06), 0 18px 34px -24px rgba(16, 39, 31, 0.28)',
        raised: '0 12px 28px -18px rgba(16, 39, 31, 0.32)',
        modal: '0 28px 70px -18px rgba(9, 28, 23, 0.42)',
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
      },
    },
  },
  plugins: [],
};
