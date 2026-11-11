import { useEffect, useState } from "react";

async function request(path, options = {}) {
  const { csrf, headers, ...fetchOptions } = options;
  const requestHeaders = new Headers(headers);

  if (csrf) {
    requestHeaders.set(csrf.headerName, csrf.token);
  }

  const response = await fetch(path, {
    credentials: "include",
    ...fetchOptions,
    headers: requestHeaders,
  });
  const contentType = response.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json")
    ? await response.json()
    : null;

  if (!response.ok) {
    const error = new Error(
      body?.message ?? body?.detail ?? `Request failed (${response.status}).`,
    );
    error.status = response.status;
    throw error;
  }

  return body;
}

async function getCsrfToken() {
  return request("/api/auth/csrf");
}

function BrandMark() {
  return (
    <span className="brand-mark" aria-hidden="true">
      <svg viewBox="0 0 32 32" fill="none">
        <path
          d="M7 23.5V10.8c0-1.6 1.8-2.5 3.1-1.5l5.9 4.5 5.9-4.5c1.3-1 3.1-.1 3.1 1.5v12.7"
          stroke="currentColor"
          strokeWidth="2.4"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M10.5 23.5h11"
          stroke="currentColor"
          strokeWidth="2.4"
          strokeLinecap="round"
        />
      </svg>
    </span>
  );
}

function App() {
  const [user, setUser] = useState(null);
  const [csrf, setCsrf] = useState(null);
  const [mode, setMode] = useState("login");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [dashboard, setDashboard] = useState(null);
  const [dashboardLoading, setDashboardLoading] = useState(false);
  const [dashboardRefresh, setDashboardRefresh] = useState(0);
  const [courseDraft, setCourseDraft] = useState(null);

  useEffect(() => {
    let active = true;

    async function restoreSession() {
      try {
        const token = await getCsrfToken();
        if (active) {
          setCsrf(token);
        }

        try {
          const currentUser = await request("/api/auth/me");
          if (active) {
            setUser(currentUser);
          }
        } catch (sessionError) {
          if (sessionError.status !== 401) {
            throw sessionError;
          }
        }
      } catch {
        if (active) {
          setError("Could not reach LearnOn. Make sure the backend is running, then try again.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    restoreSession();
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!user) {
      setDashboard(null);
      return undefined;
    }

    let active = true;
    setDashboardLoading(true);

    async function loadDashboard() {
      try {
        if (user.role === "TEACHER") {
          const courses = await request("/api/courses/owned");
          const managedCourses = await Promise.all(courses.map(async (course) => {
            const [enrollments, progress] = await Promise.all([
              request(`/api/enrollments/course/${course.id}`),
              request(`/api/progress/course/${course.id}`),
            ]);
            const progressByUserId = new Map(
              progress.map((record) => [record.user.id, record]),
            );
            return {
              ...course,
              enrollments: enrollments.map((enrollment) => ({
                ...enrollment,
                progress: progressByUserId.get(enrollment.user.id) ?? null,
              })),
            };
          }));
          if (active) {
            setDashboard({ courses: managedCourses });
          }
        } else {
          const [courses, enrollments, progress] = await Promise.all([
            request("/api/courses/published"),
            request(`/api/enrollments/user/${user.id}`),
            request(`/api/progress/user/${user.id}`),
          ]);
          if (active) {
            setDashboard({
              courses,
              enrollments,
              progressByCourseId: new Map(
                progress.map((record) => [record.course.id, record]),
              ),
            });
          }
        }
      } catch (dashboardError) {
        if (active) {
          setError(dashboardError.message || "Could not load your dashboard.");
        }
      } finally {
        if (active) {
          setDashboardLoading(false);
        }
      }
    }

    loadDashboard();
    return () => {
      active = false;
    };
  }, [user, dashboardRefresh]);

  function showError(message) {
    setNotice("");
    setError(message);
  }

  async function runDashboardAction(action, successMessage) {
    setSubmitting(true);
    setError("");
    setNotice("");
    try {
      const token = csrf ?? await getCsrfToken();
      setCsrf(token);
      await action(token);
      setNotice(successMessage);
      setDashboardRefresh((value) => value + 1);
    } catch (actionError) {
      showError(actionError.message || "The action could not be completed.");
    } finally {
      setSubmitting(false);
    }
  }

  async function changeAccountRole(role) {
    await runDashboardAction(async (token) => {
      const updatedUser = await request("/api/auth/role", {
        method: "PUT",
        csrf: token,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ role }),
      });
      setUser(updatedUser);
      setDashboard(null);
    }, `Your account is now in ${role.toLowerCase()} mode.`);
  }

  async function saveCourse(event) {
    event.preventDefault();
    const editing = Boolean(courseDraft.id);
    await runDashboardAction(async (token) => {
      const course = {
        title: courseDraft.title.trim(),
        description: courseDraft.description.trim(),
        published: courseDraft.published,
      };
      await request(
        editing ? `/api/courses/${courseDraft.id}` : "/api/courses",
        {
          method: editing ? "PUT" : "POST",
          csrf: token,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(course),
        },
      );
      setCourseDraft(null);
    }, editing ? "Course changes saved." : "Course created.");
  }

  async function enrollInCourse(courseId) {
    await runDashboardAction(async (token) => {
      await request(`/api/enrollments?userId=${user.id}&courseId=${courseId}`, {
        method: "POST",
        csrf: token,
      });
    }, "You are enrolled in the course.");
  }

  async function updateProgress(courseId, event) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const completedLessons = Number(values.get("completedLessons"));
    const totalLessons = Number(values.get("totalLessons"));
    await runDashboardAction(async (token) => {
      await request(
        `/api/progress?userId=${user.id}&courseId=${courseId}&completedLessons=${completedLessons}&totalLessons=${totalLessons}`,
        { method: "PUT", csrf: token },
      );
    }, "Your progress has been updated.");
  }

  async function addLearner(courseId, event) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(event.currentTarget);
    const email = String(values.get("email")).trim();
    await runDashboardAction(async (token) => {
      await request(
        `/api/courses/${courseId}/learners?email=${encodeURIComponent(email)}`,
        { method: "POST", csrf: token },
      );
      form.reset();
    }, "Learner added to the course.");
  }

  async function removeEnrollment(enrollmentId) {
    await runDashboardAction(async (token) => {
      await request(`/api/enrollments/${enrollmentId}`, {
        method: "DELETE",
        csrf: token,
      });
    }, "Learner removed from the course.");
  }

  async function deleteCourse(courseId) {
    await runDashboardAction(async (token) => {
      await request(`/api/courses/${courseId}`, {
        method: "DELETE",
        csrf: token,
      });
    }, "Course deleted.");
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    setNotice("");

    const formData = new FormData(event.currentTarget);
    const email = String(formData.get("email")).trim().toLowerCase();
    const password = String(formData.get("password"));

    try {
      const token = csrf ?? await getCsrfToken();
      setCsrf(token);

      if (mode === "register") {
        const payload = {
          firstName: String(formData.get("firstName")).trim(),
          lastName: String(formData.get("lastName")).trim(),
          email,
          password,
        };
        await request("/api/users", {
          method: "POST",
          csrf: token,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });
        setMode("login");
        setNotice("Your account is ready. Sign in to continue.");
        return;
      }

      await request("/api/auth/login", {
        method: "POST",
        csrf: token,
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ email, password }),
      });

      const refreshedToken = await getCsrfToken();
      const currentUser = await request("/api/auth/me");
      setCsrf(refreshedToken);
      setUser(currentUser);
    } catch (submitError) {
      if (submitError.status === 401) {
        showError("That email and password combination was not recognized.");
      } else if (submitError.status === 409) {
        showError("An account with that email already exists. Try signing in instead.");
      } else if (submitError.status === 400) {
        showError("Please check your details. Passwords must be at least 8 characters.");
      } else {
        showError(submitError.message || "Something went wrong. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleLogout() {
    setSubmitting(true);
    setError("");

    try {
      const token = csrf ?? await getCsrfToken();
      await request("/api/auth/logout", { method: "POST", csrf: token });
      setUser(null);
      setCsrf(await getCsrfToken());
      setNotice("You have been signed out.");
    } catch (logoutError) {
      showError(logoutError.message || "Could not sign out. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  function changeMode(nextMode) {
    setMode(nextMode);
    setError("");
    setNotice("");
  }

  return (
    <main className="page-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="LearnOn home">
          <BrandMark />
          <span>learn<span className="brand-on">on</span></span>
        </a>
        <span className="topbar-note">
          {user ? "Your learning space" : "A little progress, every day"}
        </span>
      </header>

      <section className={`auth-layout${user ? " auth-layout-dashboard" : ""}`}>
        <div className="story-panel">
          <div className="story-content">
            <div className="eyebrow"><span className="eyebrow-dot" /> YOUR NEXT CHAPTER</div>
            <h1>Curiosity looks good <em>on you.</em></h1>
            <p className="story-copy">
              Make room for new ideas, useful skills, and the kind of progress
              that adds up.
            </p>
            <div className="quote-card">
              <span className="quote-mark">“</span>
              <p>Small steps every day lead to big changes over time.</p>
              <div className="quote-attribution">
                <span className="avatar-stack" aria-hidden="true">
                  <i>A</i><i>M</i><i>J</i>
                </span>
                <span>Good things start here</span>
              </div>
            </div>
          </div>
          <div className="story-decoration story-decoration-one" />
          <div className="story-decoration story-decoration-two" />
          <div className="story-sparkle sparkle-one">✳</div>
          <div className="story-sparkle sparkle-two">✳</div>
          <span className="story-footnote">LEARN SOMETHING. BECOME SOMETHING.</span>
        </div>

        <div className="form-panel">
          {loading ? (
            <div className="loading-state" role="status">
              <span className="spinner" />
              <span>Opening your learning space…</span>
            </div>
          ) : user ? (
            <div className="dashboard">
              <div className="dashboard-heading">
                <div>
                  <div className="eyebrow">YOUR LEARNING SPACE</div>
                  <h2>Welcome back, {user.firstName}.</h2>
                  <p className="form-intro">Choose how you want to use LearnOn today.</p>
                </div>
                <label className="role-picker">
                  <span>Account mode</span>
                  <select
                    value={user.role}
                    onChange={(event) => changeAccountRole(event.target.value)}
                    disabled={submitting}
                    aria-label="Choose learner or teacher account mode"
                  >
                    <option value="LEARNER">Learner</option>
                    <option value="TEACHER">Teacher</option>
                  </select>
                </label>
              </div>

              <div className="profile-card dashboard-profile">
                <span className="profile-avatar">
                  {user.firstName?.charAt(0)}{user.lastName?.charAt(0)}
                </span>
                <span className="profile-details">
                  <strong>{user.firstName} {user.lastName}</strong>
                  <span>{user.email}</span>
                </span>
                <button
                  className="button button-secondary signout-button"
                  type="button"
                  onClick={handleLogout}
                  disabled={submitting}
                >
                  {submitting ? "Signing out…" : "Sign out"}
                </button>
              </div>

              {error && <p className="alert alert-error" role="alert">{error}</p>}
              {notice && <p className="alert alert-success" role="status">{notice}</p>}

              {dashboardLoading ? (
                <div className="loading-state dashboard-loading" role="status">
                  <span className="spinner" />
                  <span>Loading your courses…</span>
                </div>
              ) : user.role === "TEACHER" ? (
                <TeacherDashboard
                  dashboard={dashboard}
                  courseDraft={courseDraft}
                  setCourseDraft={setCourseDraft}
                  onSaveCourse={saveCourse}
                  onDeleteCourse={deleteCourse}
                  onAddLearner={addLearner}
                  onRemoveEnrollment={removeEnrollment}
                  submitting={submitting}
                />
              ) : (
                <LearnerDashboard
                  dashboard={dashboard}
                  onEnroll={enrollInCourse}
                  onUpdateProgress={updateProgress}
                  submitting={submitting}
                />
              )}
            </div>
          ) : (
            <div className="form-card">
              <div className="form-heading">
                <div className="eyebrow">{mode === "login" ? "WELCOME BACK" : "START YOUR JOURNEY"}</div>
                <h2>{mode === "login" ? "Good to see you." : "Create your account."}</h2>
                <p className="form-intro">
                  {mode === "login"
                    ? "Sign in and pick up where you left off."
                    : "A few details and you’re ready to learn."}
                </p>
              </div>

              <div className="mode-switch" role="tablist" aria-label="Account action">
                <button
                  type="button"
                  role="tab"
                  aria-selected={mode === "login"}
                  className={mode === "login" ? "active" : ""}
                  onClick={() => changeMode("login")}
                >
                  Sign in
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={mode === "register"}
                  className={mode === "register" ? "active" : ""}
                  onClick={() => changeMode("register")}
                >
                  Create account
                </button>
              </div>

              {error && <p className="alert alert-error" role="alert">{error}</p>}
              {notice && <p className="alert alert-success" role="status">{notice}</p>}

              <form className="auth-form" onSubmit={handleSubmit}>
                {mode === "register" && (
                  <div className="name-row">
                    <label className="field">
                      <span>First name</span>
                      <input name="firstName" type="text" autoComplete="given-name" placeholder="Alex" required maxLength="60" />
                    </label>
                    <label className="field">
                      <span>Last name</span>
                      <input name="lastName" type="text" autoComplete="family-name" placeholder="Santos" required maxLength="60" />
                    </label>
                  </div>
                )}

                <label className="field">
                  <span>Email address</span>
                  <span className="input-wrap">
                    <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                      <rect x="2.5" y="4" width="15" height="12" rx="2" stroke="currentColor" strokeWidth="1.5" />
                      <path d="m3.5 5 6.5 5 6.5-5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                    <input name="email" type="email" autoComplete="email" placeholder="you@example.com" required maxLength="120" />
                  </span>
                </label>

                <label className="field">
                  <span className="label-line">
                    <span>Password</span>
                    {mode === "login" && <span className="field-hint">At least 8 characters</span>}
                  </span>
                  <span className="input-wrap">
                    <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                      <rect x="3.5" y="8.5" width="13" height="9" rx="2" stroke="currentColor" strokeWidth="1.5" />
                      <path d="M6.5 8.5V6a3.5 3.5 0 0 1 7 0v2.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
                      <circle cx="10" cy="12.8" r="1" fill="currentColor" />
                    </svg>
                    <input
                      name="password"
                      type="password"
                      autoComplete={mode === "login" ? "current-password" : "new-password"}
                      placeholder="••••••••"
                      required
                      minLength="8"
                      maxLength="72"
                    />
                  </span>
                  {mode === "register" && <span className="field-hint">Use 8 or more characters.</span>}
                </label>

                <button className="button button-primary" type="submit" disabled={submitting}>
                  {submitting
                    ? (mode === "login" ? "Signing in…" : "Creating account…")
                    : (mode === "login" ? "Sign in" : "Create account")}
                  {!submitting && <span aria-hidden="true">→</span>}
                </button>
              </form>

              <p className="terms-note">
                By continuing, you agree to use LearnOn responsibly and keep your
                account details safe.
              </p>
            </div>
          )}
        </div>
      </section>

      <footer className="page-footer">
        <span>© {new Date().getFullYear()} LearnOn</span>
        <span className="footer-center"><i /> BUILT FOR THE LONG GAME</span>
        <span>Keep growing.</span>
      </footer>
    </main>
  );
}

function LearnerDashboard({ dashboard, onEnroll, onUpdateProgress, submitting }) {
  const enrollments = dashboard?.enrollments ?? [];
  const enrolledCourseIds = new Set(enrollments.map((entry) => entry.course.id));
  const availableCourses = (dashboard?.courses ?? []).filter(
    (course) => !enrolledCourseIds.has(course.id),
  );
  const progressByCourseId = dashboard?.progressByCourseId ?? new Map();

  return (
    <div className="role-dashboard">
      <section className="dashboard-section">
        <div className="section-heading">
          <div>
            <div className="eyebrow">YOUR JOURNEY</div>
            <h3>Courses in progress <span className="count-pill">{enrollments.length}</span></h3>
          </div>
        </div>
        {enrollments.length === 0 ? (
          <p className="empty-state">You haven’t enrolled in a course yet. Find a course below to get started.</p>
        ) : (
          <div className="course-grid">
            {enrollments.map((enrollment) => {
              const course = enrollment.course;
              const progress = progressByCourseId.get(course.id);
              const percentage = Math.round(progress?.completionPercentage ?? 0);
              return (
                <article className="course-card learner-course" key={enrollment.id}>
                  <div className="course-card-topline">
                    <span className="course-kicker">ENROLLED COURSE</span>
                    <span className="status-pill">In progress</span>
                  </div>
                  <h4>{course.title}</h4>
                  <p>{course.description || "No course description yet."}</p>
                  <div className="progress-summary">
                    <div className="progress-label">
                      <span>Your progress</span>
                      <strong>{percentage}%</strong>
                    </div>
                    <div className="progress-track" aria-label={`${percentage}% complete`}>
                      <span style={{ width: `${percentage}%` }} />
                    </div>
                    <span className="progress-caption">
                      {progress
                        ? `${progress.completedLessons} of ${progress.totalLessons} lessons completed`
                        : "No lessons completed yet"}
                    </span>
                  </div>
                  <form
                    className="progress-form"
                    onSubmit={(event) => onUpdateProgress(course.id, event)}
                  >
                    <label>
                      Completed
                      <input
                        type="number"
                        name="completedLessons"
                        min="0"
                        defaultValue={progress?.completedLessons ?? 0}
                        key={`${course.id}-${progress?.completedLessons ?? 0}`}
                        readOnly={submitting}
                        required
                      />
                    </label>
                    <label>
                      Total lessons
                      <input
                        type="number"
                        name="totalLessons"
                        min="0"
                        defaultValue={progress?.totalLessons ?? 0}
                        key={`${course.id}-${progress?.totalLessons ?? 0}`}
                        required
                      />
                    </label>
                    <button className="button button-primary small-button" disabled={submitting}>
                      Update progress
                    </button>
                  </form>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <section className="dashboard-section">
        <div className="section-heading">
          <div>
            <div className="eyebrow">KEEP DISCOVERING</div>
            <h3>Explore courses</h3>
          </div>
        </div>
        {availableCourses.length === 0 ? (
          <p className="empty-state">
            {enrollments.length ? "You’re enrolled in every available course." : "No published courses are available yet."}
          </p>
        ) : (
          <div className="course-grid">
            {availableCourses.map((course) => (
              <article className="course-card" key={course.id}>
                <div className="course-card-topline">
                  <span className="course-kicker">LEARN SOMETHING NEW</span>
                  <span className="status-pill">Available</span>
                </div>
                <h4>{course.title}</h4>
                <p>{course.description || "A new course to explore at your own pace."}</p>
                <button
                  className="button button-primary small-button"
                  type="button"
                  onClick={() => onEnroll(course.id)}
                  disabled={submitting}
                >
                  Enroll in course <span aria-hidden="true">→</span>
                </button>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function TeacherDashboard({
  dashboard,
  courseDraft,
  setCourseDraft,
  onSaveCourse,
  onDeleteCourse,
  onAddLearner,
  onRemoveEnrollment,
  submitting,
}) {
  const courses = dashboard?.courses ?? [];

  return (
    <div className="role-dashboard">
      <section className="dashboard-section">
        <div className="section-heading">
          <div>
            <div className="eyebrow">TEACHER STUDIO</div>
            <h3>Your courses <span className="count-pill">{courses.length}</span></h3>
          </div>
          {!courseDraft && (
            <button
              className="button button-primary small-button"
              type="button"
              onClick={() => setCourseDraft({
                id: null,
                title: "",
                description: "",
                published: true,
              })}
            >
              + Add course
            </button>
          )}
        </div>

        {courseDraft && (
          <form className="course-editor" onSubmit={onSaveCourse}>
            <div className="editor-heading">
              <h4>{courseDraft.id ? "Edit course" : "Create a course"}</h4>
              <button
                className="icon-button"
                type="button"
                onClick={() => setCourseDraft(null)}
                aria-label="Close course editor"
              >
                ×
              </button>
            </div>
            <label className="field">
              <span>Course title</span>
              <input
                value={courseDraft.title}
                onChange={(event) => setCourseDraft({ ...courseDraft, title: event.target.value })}
                required
                maxLength="150"
                placeholder="e.g. Introduction to design"
              />
            </label>
            <label className="field">
              <span>Description</span>
              <textarea
                value={courseDraft.description}
                onChange={(event) => setCourseDraft({ ...courseDraft, description: event.target.value })}
                rows="3"
                placeholder="What will learners explore?"
              />
            </label>
            <label className="checkbox-field">
              <input
                type="checkbox"
                checked={courseDraft.published}
                onChange={(event) => setCourseDraft({ ...courseDraft, published: event.target.checked })}
              />
              Publish so learners can enroll
            </label>
            <div className="editor-actions">
              <button className="button button-primary small-button" disabled={submitting}>
                {courseDraft.id ? "Save changes" : "Create course"}
              </button>
              <button
                className="button button-secondary small-button"
                type="button"
                onClick={() => setCourseDraft(null)}
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        {courses.length === 0 && !courseDraft ? (
          <p className="empty-state">Create your first course to start building your learning community.</p>
        ) : (
          <div className="teacher-course-list">
            {courses.map((course) => (
              <article className="teacher-course-card" key={course.id}>
                <div className="teacher-course-heading">
                  <div>
                    <div className="course-card-topline">
                      <span className="course-kicker">YOUR COURSE</span>
                      <span className={`status-pill${course.published ? "" : " status-draft"}`}>
                        {course.published ? "Published" : "Draft"}
                      </span>
                    </div>
                    <h4>{course.title}</h4>
                    <p>{course.description || "No description added."}</p>
                  </div>
                  <div className="course-actions">
                    <button
                      className="text-button"
                      type="button"
                      onClick={() => setCourseDraft({
                        id: course.id,
                        title: course.title,
                        description: course.description ?? "",
                        published: course.published,
                      })}
                    >
                      Edit
                    </button>
                    <button
                      className="text-button danger-text"
                      type="button"
                      disabled={submitting}
                      onClick={() => onDeleteCourse(course.id)}
                    >
                      Delete
                    </button>
                  </div>
                </div>

                <div className="learner-list-heading">
                  <strong>Learners <span className="count-pill">{course.enrollments.length}</span></strong>
                  <span>Progress in this course</span>
                </div>
                {course.enrollments.length === 0 ? (
                  <p className="empty-inline">No learners enrolled yet.</p>
                ) : (
                  <div className="learner-list">
                    {course.enrollments.map((enrollment) => {
                      const progress = enrollment.progress;
                      const percentage = Math.round(progress?.completionPercentage ?? 0);
                      return (
                        <div className="learner-row" key={enrollment.id}>
                          <span className="learner-avatar">
                            {enrollment.user.firstName?.charAt(0)}{enrollment.user.lastName?.charAt(0)}
                          </span>
                          <span className="learner-name">
                            <strong>{enrollment.user.firstName} {enrollment.user.lastName}</strong>
                            <span>{enrollment.user.email}</span>
                          </span>
                          <span className="learner-progress">
                            <strong>{percentage}%</strong>
                            <span className="progress-track"><span style={{ width: `${percentage}%` }} /></span>
                          </span>
                          <button
                            className="text-button danger-text"
                            type="button"
                            disabled={submitting}
                            onClick={() => onRemoveEnrollment(enrollment.id)}
                          >
                            Remove
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}

                <form className="add-learner-form" onSubmit={(event) => onAddLearner(course.id, event)}>
                  <label className="field">
                    <span>Add learner by email</span>
                    <input type="email" name="email" placeholder="learner@example.com" required />
                  </label>
                  <button className="button button-secondary small-button" disabled={submitting}>
                    Add learner
                  </button>
                </form>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

export default App;
