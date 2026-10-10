const navigationItems = [
  {
    id: 'home',
    label: 'Home',
    icon: '⌂',
  },
  {
    id: 'courses',
    label: 'Courses',
    icon: '▤',
  },
  {
    id: 'groups',
    label: 'Groups',
    icon: '♙',
  },
  {
    id: 'certificates',
    label: 'Certs',
    icon: '◇',
  },
  {
    id: 'profile',
    label: 'You',
    icon: '○',
  },
]

function BottomNav() {
  return (
    <nav
      className="bottom-nav"
      aria-label="Main navigation"
    >
      {navigationItems.map((item) => (
        <button
          key={item.id}
          type="button"
          className={`nav-item ${
            item.id === 'home' ? 'active' : ''
          }`}
        >
          <span
            className="nav-icon"
            aria-hidden="true"
          >
            {item.icon}
          </span>

          <span>{item.label}</span>
        </button>
      ))}
    </nav>
  )
}

export default BottomNav