import { useEffect, useState } from 'react'
import './App.css'

import Header from './components/Header.jsx'
import StatCard from './components/StatCard.jsx'
import ContinueLearning from './components/ContinueLearning.jsx'
import MilestoneCard from './components/MilestoneCard.jsx'
import BottomNav from './components/BottomNav.jsx'

import {
  user,
  stats,
  currentCourse,
  milestones,
} from './data/dashboardData.jsx'

function App() {
  const [theme, setTheme] = useState(() => {
    return localStorage.getItem('learnon-theme') || 'dark'
  })

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    localStorage.setItem('learnon-theme', theme)
  }, [theme])

  const toggleTheme = () => {
    setTheme((currentTheme) =>
      currentTheme === 'dark' ? 'light' : 'dark',
    )
  }

  return (
    <div className="app">
      <main className="dashboard">
        <Header
          userName={user.name}
          theme={theme}
          onToggleTheme={toggleTheme}
        />

        <section
          className="stats-grid"
          aria-label="Learning statistics"
        >
          {stats.map((stat) => (
            <StatCard
              key={stat.id}
              icon={stat.icon}
              value={stat.value}
              label={stat.label}
            />
          ))}
        </section>

        <div className="dashboard-content-grid">
          <ContinueLearning course={currentCourse} />

          <section className="dashboard-section milestones-section">
            <div className="section-heading">
              <div>
                <p className="section-eyebrow">
                  This Week&apos;s Milestones
                </p>

                <h2>Keep the momentum going</h2>
              </div>
            </div>

            <div className="milestone-list">
              {milestones.map((milestone) => (
                <MilestoneCard
                  key={milestone.id}
                  icon={milestone.icon}
                  title={milestone.title}
                  description={milestone.description}
                  progress={milestone.progress}
                />
              ))}
            </div>
          </section>
        </div>
      </main>

      <BottomNav />
    </div>
  )
}

export default App