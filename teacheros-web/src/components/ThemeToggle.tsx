import { useTheme } from '@/lib/theme'

export function ThemeToggle({ className = 'text-slate hover:bg-paper-dim hover:text-ink' }: { className?: string }) {
  const { theme, toggleTheme } = useTheme()
  const dark = theme === 'dark'

  const icon = 'absolute h-[18px] w-[18px] transition-all duration-300'
  return (
    <button
      type="button"
      onClick={toggleTheme}
      role="switch"
      aria-checked={dark}
      aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'}
      title={dark ? 'Light mode' : 'Dark mode'}
      className={`relative h-8 w-8 shrink-0 rounded-lg flex items-center justify-center overflow-hidden transition-colors duration-150 active:scale-90 ${className}`}
    >
      <svg
        viewBox="0 0 24 24"
        className={`${icon} ${dark ? 'rotate-90 scale-0 opacity-0' : 'rotate-0 scale-100 opacity-100'}`}
        fill="none"
        stroke="currentColor"
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden
      >
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2 M12 20v2 M4.93 4.93l1.41 1.41 M17.66 17.66l1.41 1.41 M2 12h2 M20 12h2 M4.93 19.07l1.41-1.41 M17.66 6.34l1.41-1.41" />
      </svg>
      <svg
        viewBox="0 0 24 24"
        className={`${icon} ${dark ? 'rotate-0 scale-100 opacity-100' : '-rotate-90 scale-0 opacity-0'}`}
        fill="none"
        stroke="currentColor"
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden
      >
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
      </svg>
    </button>
  )
}
