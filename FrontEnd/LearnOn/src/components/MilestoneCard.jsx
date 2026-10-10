function MilestoneCard({
  icon,
  title,
  description,
  progress,
}) {
  return (
    <article className="milestone-card">
      <div className="milestone-icon" aria-hidden="true">
        {icon}
      </div>

      <div className="milestone-content">
        <div className="milestone-header">
          <div>
            <h3>{title}</h3>
            <p>{description}</p>
          </div>

          <strong>{progress}%</strong>
        </div>

        <div
          className="progress-track milestone-progress"
          role="progressbar"
          aria-valuenow={progress}
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <div
            className="progress-fill"
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>
    </article>
  )
}

export default MilestoneCard