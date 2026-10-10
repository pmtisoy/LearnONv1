function ContinueLearning({ course }) {
  const handleResumeLesson = () => {
    console.log(`Resume: ${course.lessonTitle}`)
  }

  return (
    <section className="dashboard-section">
      <div className="section-heading">
        <div>
          <p className="section-eyebrow">Continue Learning</p>
          <h2>Pick up where you left off</h2>
        </div>

        <span className="progress-percentage">
          {course.progress}%
        </span>
      </div>

      <article className="learning-card">
        <div className="course-thumbnail">
          <div className="thumbnail-content">
            <span>STAT</span>
          </div>
        </div>

        <div className="course-information">
          <div>
            <span className="course-module">
              {course.module}
            </span>

            <h3>{course.courseTitle}</h3>

            <p className="lesson-name">
              {course.lessonTitle}
            </p>

            <p className="lesson-count">
              {course.lesson}
            </p>
          </div>

          <div className="course-progress-container">
            <div className="progress-info">
              <span>Course progress</span>
              <strong>{course.progress}%</strong>
            </div>

            <div
              className="progress-track"
              role="progressbar"
              aria-valuenow={course.progress}
              aria-valuemin="0"
              aria-valuemax="100"
            >
              <div
                className="progress-fill"
                style={{ width: `${course.progress}%` }}
              />
            </div>
          </div>

          <button
            type="button"
            className="resume-button"
            onClick={handleResumeLesson}
          >
            Resume Lesson
            <span aria-hidden="true">→</span>
          </button>
        </div>
      </article>
    </section>
  )
}

export default ContinueLearning