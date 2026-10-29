/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      /* ========================================================================
         SmartHire visual system — "The hiring record, in colour".
         Warm paper, ink type, tangerine action, and five stage colours.
         Legacy token names stay intact so every screen inherits the system.
         ======================================================================== */
      colors: {
        /* ---------- Neutral surfaces ---------- */
        'c-bg': '#FBF6EC',
        'c-surface': '#FFFFFF',
        'c-surface-sunken': '#F6EFE3',
        'c-surface-raised': '#FFFFFF',
        'c-border': '#E8DDCB',
        'c-border-strong': '#D6C5AD',
        'c-text': '#191512',
        'c-text-muted': '#6B5F55',
        'c-text-subtle': '#94867A',
        'c-overlay': 'rgba(25, 21, 18, 0.50)',
        'c-scrim': 'rgba(25, 21, 18, 0.30)',

        /* on-surface aliases */
        'on-surface': '#191512',
        'on-surface-variant': '#6B5F55',

        /* ---------- Primary (tangerine) ---------- */
        'c-primary-50': '#FFF3EE',
        'c-primary-100': '#FFE2D6',
        'c-primary-200': '#FFC7B0',
        'c-primary-300': '#FFA184',
        'c-primary-400': '#FB7A52',
        'c-primary-500': '#F05A28',
        'c-primary-600': '#D94615',
        'c-primary-700': '#B4360D',
        'c-primary-800': '#8F2C0B',
        'c-primary-900': '#74250C',
        'c-primary-950': '#3F1104',

        'on-primary': '#FFFFFF',

        /* ---------- Accent (stage lime) ---------- */
        'c-accent-50': '#FAFFE8',
        'c-accent-100': '#F2FFC2',
        'c-accent-200': '#E7FF8A',
        'c-accent-300': '#D7F55F',
        'c-accent-400': '#C4E83E',
        'c-accent-500': '#A8CC26',
        'c-accent-600': '#86A61A',
        'c-accent-700': '#667F16',
        'c-accent-800': '#4E6115',

        /* ---------- Semantic: success ---------- */
        'success-50': '#E9F8EF',
        'success-100': '#D3F1E0',
        'success-200': '#A9E5C3',
        'success-500': '#2E9E64',
        'success-600': '#1E7A4C',
        'success-700': '#17613C',
        'success-800': '#124D30',

        'on-success': '#FFFFFF',

        /* ---------- Semantic: warning ---------- */
        'warning-50': '#FFF8E3',
        'warning-100': '#FFEFC2',
        'warning-200': '#FFE08A',
        'warning-500': '#D99A16',
        'warning-600': '#B37A0A',
        'warning-700': '#8A5A00',
        'warning-800': '#6B4600',

        'on-warning': '#FFFFFF',

        /* ---------- Semantic: danger ---------- */
        'danger-50': '#FDECEA',
        'danger-100': '#FBD9D5',
        'danger-200': '#F5B5AE',
        'danger-500': '#D6453A',
        'danger-600': '#B3261E',
        'danger-700': '#8F1D17',
        'danger-800': '#6E1712',

        'on-danger': '#FFFFFF',

        /* ---------- Semantic: info ---------- */
        'info-50': '#EAF4FF',
        'info-100': '#D6E9FF',
        'info-200': '#ADD3FF',
        'info-500': '#3A82C9',
        'info-600': '#1F5FA8',
        'info-700': '#174A85',
        'info-800': '#123A69',

        'on-info': '#FFFFFF',

        /* ---------- Natural aliases ---------- */
        surface: '#FFFFFF',
        'surface-sunken': '#F6EFE3',
        background: '#FBF6EC',
        border: '#E8DDCB',
        primary: '#191512',
        secondary: '#F05A28',
      },

      /* ---------- Typography ---------- */
      fontFamily: {
        sans: ['Instrument Sans', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'Helvetica', 'Arial', 'sans-serif'],
        display: ['Bricolage Grotesque', 'Instrument Sans', 'system-ui', 'sans-serif'],
        serif: ['Newsreader', 'Georgia', 'Cambria', 'Times New Roman', 'serif'],
        mono: ['IBM Plex Mono', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
      fontSize: {
        'display-xl': ['80px', { lineHeight: '0.94', fontWeight: '600', letterSpacing: '-0.05em' }],
        display: ['54px', { lineHeight: '0.98', fontWeight: '600', letterSpacing: '-0.045em' }],
        'headline-lg': ['34px', { lineHeight: '1.12', fontWeight: '600', letterSpacing: '-0.035em' }],
        'headline-md': ['25px', { lineHeight: '1.2', fontWeight: '600', letterSpacing: '-0.025em' }],
        'headline-sm': ['20px', { lineHeight: '1.3', fontWeight: '600', letterSpacing: '-0.015em' }],
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
        topbar: '4.75rem',
        sidebar: '17rem',
        'sidebar-rail': '5rem',
      },

      maxWidth: {
        content: '84rem',
        prose: '68ch',
        narrow: '48rem',
      },

      borderRadius: {
        sm: '0.375rem',
        DEFAULT: '0.75rem',
        md: '0.875rem',
        lg: '1rem',
        xl: '1.25rem',
        '2xl': '1.5rem',
        '3xl': '2rem',
        full: '9999px',
      },

      boxShadow: {
        resting: '0 1px 2px rgba(64, 45, 30, 0.04), 0 10px 28px -22px rgba(64, 45, 30, 0.22)',
        hover: '0 2px 4px rgba(64, 45, 30, 0.05), 0 20px 40px -24px rgba(64, 45, 30, 0.28)',
        raised: '0 28px 56px -30px rgba(64, 45, 30, 0.34)',
        modal: '0 32px 80px -24px rgba(25, 21, 18, 0.34)',
        inset: 'inset 0 1px 0 rgba(255, 255, 255, 0.18)',
        pop: '5px 5px 0 #191512',
        'pop-sm': '3px 3px 0 #191512',
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
