function StatCard({ icon, value, label }) {
  return (
    <article className="stat-card">
      <div className="stat-top-row">
        <span className="stat-icon" aria-hidden="true">
          {icon}
        </span>

        <strong className="stat-value">{value}</strong>
      </div>

      <span className="stat-label">{label}</span>
    </article>
  )
}

<StatCard
  icon="🔥"
  value="12"
  label="Day Streak"
/>

export default StatCard