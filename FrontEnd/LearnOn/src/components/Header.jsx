function Header({
  userName,
  theme,
  onToggleTheme,
}) {
  return (
    <header className="dashboard-header">
      <div>
        <p className="app-name">LearnON</p>

        <h1>
          Hi, {userName}{' '}
          <span aria-hidden="true">👋</span>
        </h1>

        <p className="header-subtitle">
          Keep learning and maintain your streak.
        </p>
      </div>

      <div className="header-actions">
        <button
          type="button"
          className="theme-toggle"
          onClick={onToggleTheme}
          aria-label={
            theme === 'dark'
              ? 'Switch to light mode'
              : 'Switch to dark mode'
          }
          title={
            theme === 'dark'
              ? 'Switch to light mode'
              : 'Switch to dark mode'
          }
        >
          <span
            className="theme-icon"
            aria-hidden="true"
          >
            {theme === 'dark' ? '☀️' : '🌙'}
          </span>
        </button>

        <button
          type="button"
          className="profile-button"
          aria-label="Open profile"
        >
          M
        </button>
      </div>
    </header>
  )
}

export default Header