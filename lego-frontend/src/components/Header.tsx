export default function Header() {
  return (
    <header className="border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 px-6 py-4">
      <div className="max-w-2xl mx-auto flex items-center gap-2">
        <svg
          className="w-5 h-5 text-gray-700 dark:text-gray-300 shrink-0"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.75"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <rect x="2" y="7" width="6" height="6" rx="1" />
          <rect x="9" y="7" width="6" height="6" rx="1" />
          <rect x="16" y="7" width="6" height="6" rx="1" />
          <rect x="5" y="14" width="6" height="6" rx="1" />
          <rect x="13" y="14" width="6" height="6" rx="1" />
        </svg>
        <span className="text-base font-semibold tracking-tight text-gray-900 dark:text-gray-100">
          Lego Architecture Engine
        </span>
      </div>
    </header>
  )
}
