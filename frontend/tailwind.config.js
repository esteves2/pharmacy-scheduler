/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{ts,tsx}'
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: '#1a5c38',
          dark:    '#134429',
          light:   '#2a7a4b',
          faint:   '#eef6f1',
        },
        gold: {
          DEFAULT: '#c9a84c',
          light:   '#f0d98a',
        },
      },
    }
  },
  plugins: []
}
