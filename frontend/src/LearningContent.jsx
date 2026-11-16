import { useEffect, useState } from "react";
import { getCsrfToken, request } from "./api.js";

const emptyQuestion = () => ({
  prompt: "",
  choices: ["", "", "", ""],
  correctChoice: 1,
});

function localDateTime(value) {
  const date = value ? new Date(value) : new Date(Date.now() + 60 * 60 * 1000);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
}

function LearningContent({ courseId, role, csrf: initialCsrf }) {
  const [csrf, setCsrf] = useState(initialCsrf);
  const [modules, setModules] = useState([]);
  const [quizzes, setQuizzes] = useState([]);
  const [moduleDraft, setModuleDraft] = useState(null);
  const [quizDraft, setQuizDraft] = useState(null);
  const [activeQuizId, setActiveQuizId] = useState(null);
  const [attempt, setAttempt] = useState(null);
  const [answers, setAnswers] = useState({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function loadContent() {
    try {
      const [nextModules, nextQuizzes] = await Promise.all([
        request(`/api/courses/${courseId}/modules`),
        request(`/api/courses/${courseId}/quizzes`),
      ]);
      setModules(nextModules);
      setQuizzes(nextQuizzes);
      setError("");
    } catch (loadError) {
      setError(loadError.message || "Could not load course content.");
    }
  }

  useEffect(() => {
    setCsrf(initialCsrf);
  }, [initialCsrf]);

  useEffect(() => {
    loadContent();
  }, [courseId, role]);

  async function mutate(path, options = {}) {
    const token = csrf ?? await getCsrfToken();
    setCsrf(token);
    return request(path, { ...options, csrf: token });
  }

  async function perform(action) {
    setBusy(true);
    setError("");
    try {
      await action();
      await loadContent();
    } catch (actionError) {
      setError(actionError.message || "The action could not be completed.");
    } finally {
      setBusy(false);
    }
  }

  function startModuleDraft(module = null) {
    setModuleDraft(module
      ? {
          id: module.id,
          title: module.title,
          type: module.type,
          content: module.content ?? "",
          fileName: module.fileName,
        }
      : { id: null, title: "", type: "TEXT", content: "", fileName: null });
  }

  async function saveModule(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData();
    formData.set("title", moduleDraft.title);
    formData.set("type", moduleDraft.type);
    if (moduleDraft.type !== "PDF") {
      formData.set("content", moduleDraft.content);
    }
    const fileInput = form.querySelector('input[name="file"]');
    const file = fileInput?.files?.[0];
    if (file) {
      formData.set("file", file);
    }
    const endpoint = moduleDraft.id
      ? `/api/modules/${moduleDraft.id}`
      : `/api/courses/${courseId}/modules`;

    await perform(async () => {
      await mutate(endpoint, {
        method: moduleDraft.id ? "PUT" : "POST",
        body: formData,
      });
      setModuleDraft(null);
    });
  }

  function startQuizDraft(quiz = null) {
    if (!quiz) {
      setQuizDraft({
        id: null,
        title: "",
        dueAt: localDateTime(null),
        questions: [emptyQuestion()],
      });
      return;
    }
    setBusy(true);
    request(`/api/quizzes/${quiz.id}`)
      .then((detail) => setQuizDraft({
        id: detail.id,
        title: detail.title,
        dueAt: localDateTime(detail.dueAt),
        questions: detail.questions.map((question) => ({
          prompt: question.prompt,
          choices: question.choices,
          correctChoice: question.correctChoice,
        })),
      }))
      .catch((loadError) => setError(loadError.message || "Could not load this quiz."))
      .finally(() => setBusy(false));
  }

  function updateQuestion(index, update) {
    setQuizDraft((current) => ({
      ...current,
      questions: current.questions.map((question, questionIndex) =>
        questionIndex === index ? { ...question, ...update } : question),
    }));
  }

  async function saveQuiz(event) {
    event.preventDefault();
    const payload = {
      title: quizDraft.title,
      dueAt: new Date(quizDraft.dueAt).toISOString(),
      questions: quizDraft.questions,
    };
    await perform(async () => {
      await mutate(
        quizDraft.id ? `/api/quizzes/${quizDraft.id}` : `/api/courses/${courseId}/quizzes`,
        {
          method: quizDraft.id ? "PUT" : "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        },
      );
      setQuizDraft(null);
    });
  }

  async function deleteQuiz(quizId) {
    await perform(async () => {
      await mutate(`/api/quizzes/${quizId}`, { method: "DELETE" });
    });
  }

  async function openAttempt(quiz) {
    setBusy(true);
    setError("");
    try {
      const nextAttempt = quiz.attempted
        ? await request(`/api/quizzes/${quiz.id}/attempt`)
        : await mutate(`/api/quizzes/${quiz.id}/attempt`, { method: "POST" });
      setActiveQuizId(quiz.id);
      setAttempt(nextAttempt);
      setAnswers(Object.fromEntries(
        nextAttempt.questions
          .filter((question) => question.selectedChoice != null)
          .map((question) => [question.id, question.selectedChoice]),
      ));
      await loadContent();
    } catch (attemptError) {
      setError(attemptError.message || "Could not open this quiz attempt.");
    } finally {
      setBusy(false);
    }
  }

  async function saveAnswers(event) {
    event.preventDefault();
    await perform(async () => {
      const updated = await mutate(`/api/quizzes/${activeQuizId}/attempt`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          answers: Object.entries(answers).map(([questionId, selectedChoice]) => ({
            questionId: Number(questionId),
            selectedChoice,
          })),
        }),
      });
      setAttempt(updated);
      setAnswers(Object.fromEntries(
        updated.questions
          .filter((question) => question.selectedChoice != null)
          .map((question) => [question.id, question.selectedChoice]),
      ));
    });
  }

  async function refreshAttempt() {
    setBusy(true);
    try {
      const updated = await request(`/api/quizzes/${activeQuizId}/attempt`);
      setAttempt(updated);
      setAnswers(Object.fromEntries(
        updated.questions
          .filter((question) => question.selectedChoice != null)
          .map((question) => [question.id, question.selectedChoice]),
      ));
      await loadContent();
    } catch (loadError) {
      setError(loadError.message || "Could not refresh the quiz result.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="learning-content">
      <h5>Learning content</h5>
      {error && <p className="alert alert-error" role="alert">{error}</p>}

      <div className="learning-block">
        <div className="learning-block-heading">
          <strong>Modules</strong>
          {role === "TEACHER" && (
            <button type="button" className="text-button" onClick={() => startModuleDraft()}>
              + Add module
            </button>
          )}
        </div>
        {modules.length === 0 && !moduleDraft && <p className="empty-inline">No modules yet.</p>}
        {modules.map((module) => (
          <article className="module-item" key={module.id}>
            <div className="module-heading">
              <strong>{module.title}</strong>
              <span className="course-kicker">{module.type}</span>
              {role === "TEACHER" && (
                <>
                  <button type="button" className="text-button" onClick={() => startModuleDraft(module)}>
                    Edit
                  </button>
                  <button
                    type="button"
                    className="text-button danger-text"
                    disabled={busy}
                    onClick={() => perform(async () => {
                      await mutate(`/api/modules/${module.id}`, { method: "DELETE" });
                    })}
                  >
                    Delete
                  </button>
                </>
              )}
            </div>
            {module.type === "TEXT" && <p className="module-text">{module.content}</p>}
            {module.type === "LINK" && (
              <a href={module.content} target="_blank" rel="noreferrer">
                Open learning link
              </a>
            )}
            {module.type === "PDF" && (
              <a href={`/api/modules/${module.id}/file`} target="_blank" rel="noreferrer">
                View {module.fileName}
              </a>
            )}
          </article>
        ))}

        {role === "TEACHER" && moduleDraft && (
          <form className="learning-form" onSubmit={saveModule}>
            <label className="field">
              <span>Lesson title</span>
              <input
                value={moduleDraft.title}
                onChange={(event) => setModuleDraft({ ...moduleDraft, title: event.target.value })}
                required
                maxLength="150"
              />
            </label>
            <label className="field">
              <span>Content type</span>
              <select
                value={moduleDraft.type}
                onChange={(event) => setModuleDraft({
                  ...moduleDraft,
                  type: event.target.value,
                  content: "",
                })}
              >
                <option value="TEXT">Plain text</option>
                <option value="LINK">Web link</option>
                <option value="PDF">PDF upload</option>
              </select>
            </label>
            {moduleDraft.type === "PDF" ? (
              <label className="field">
                <span>PDF file (maximum 20 MB){moduleDraft.fileName ? ` — current: ${moduleDraft.fileName}` : ""}</span>
                <input name="file" type="file" accept="application/pdf,.pdf" required={!moduleDraft.fileName} />
              </label>
            ) : moduleDraft.type === "LINK" ? (
              <label className="field">
                <span>Web address</span>
                <input
                  type="url"
                  value={moduleDraft.content}
                  onChange={(event) => setModuleDraft({ ...moduleDraft, content: event.target.value })}
                  placeholder="https://example.com/lesson"
                  required
                />
              </label>
            ) : (
              <label className="field">
                <span>Lesson text</span>
                <textarea
                  rows="4"
                  value={moduleDraft.content}
                  onChange={(event) => setModuleDraft({ ...moduleDraft, content: event.target.value })}
                  required
                />
              </label>
            )}
            <div className="editor-actions">
              <button className="button button-primary small-button" disabled={busy}>
                {moduleDraft.id ? "Save module" : "Add module"}
              </button>
              <button type="button" className="button button-secondary small-button" onClick={() => setModuleDraft(null)}>
                Cancel
              </button>
            </div>
          </form>
        )}
      </div>

      <div className="learning-block">
        <div className="learning-block-heading">
          <strong>Quizzes</strong>
          {role === "TEACHER" && (
            <button type="button" className="text-button" onClick={() => startQuizDraft()}>
              + Create quiz
            </button>
          )}
        </div>
        {quizzes.length === 0 && !quizDraft && <p className="empty-inline">No quizzes yet.</p>}
        {quizzes.map((quiz) => (
          <article className="quiz-item" key={quiz.id}>
            <div>
              <strong>{quiz.title}</strong>
              <p className="quiz-meta">
                {quiz.questionCount} questions · Due {new Date(quiz.dueAt).toLocaleString()}
              </p>
            </div>
            {role === "TEACHER" ? (
              <div className="course-actions">
                <button type="button" className="text-button" disabled={busy} onClick={() => startQuizDraft(quiz)}>
                  Edit
                </button>
                <button type="button" className="text-button danger-text" disabled={busy} onClick={() => deleteQuiz(quiz.id)}>
                  Delete
                </button>
              </div>
            ) : (
              <div className="quiz-learner-actions">
                {quiz.graded && <strong>Score: {quiz.score}/{quiz.totalQuestions}</strong>}
                <button
                  type="button"
                  className="button button-secondary small-button"
                  disabled={busy || (!quiz.attempted && Date.now() >= new Date(quiz.dueAt).getTime())}
                  onClick={() => openAttempt(quiz)}
                >
                  {quiz.attempted ? "View attempt" : "Start one attempt"}
                </button>
              </div>
            )}
          </article>
        ))}

        {role === "TEACHER" && quizDraft && (
          <form className="learning-form quiz-editor" onSubmit={saveQuiz}>
            <label className="field">
              <span>Quiz title</span>
              <input
                value={quizDraft.title}
                onChange={(event) => setQuizDraft({ ...quizDraft, title: event.target.value })}
                maxLength="150"
                required
              />
            </label>
            <label className="field">
              <span>Due date and time (your local time)</span>
              <input
                type="datetime-local"
                value={quizDraft.dueAt}
                onChange={(event) => setQuizDraft({ ...quizDraft, dueAt: event.target.value })}
                required
              />
            </label>
            {quizDraft.questions.map((question, questionIndex) => (
              <fieldset className="question-editor" key={questionIndex}>
                <legend>Question {questionIndex + 1}</legend>
                <label className="field">
                  <span>Question</span>
                  <textarea
                    value={question.prompt}
                    onChange={(event) => updateQuestion(questionIndex, { prompt: event.target.value })}
                    required
                  />
                </label>
                {question.choices.map((choice, choiceIndex) => (
                  <label className="choice-editor" key={choiceIndex}>
                    <input
                      type="radio"
                      name={`correct-${questionIndex}`}
                      checked={question.correctChoice === choiceIndex + 1}
                      onChange={() => updateQuestion(questionIndex, { correctChoice: choiceIndex + 1 })}
                      aria-label={`Choice ${choiceIndex + 1} is correct`}
                    />
                    <span>Choice {choiceIndex + 1}</span>
                    <input
                      value={choice}
                      onChange={(event) => {
                        const choices = [...question.choices];
                        choices[choiceIndex] = event.target.value;
                        updateQuestion(questionIndex, { choices });
                      }}
                      maxLength="500"
                      required
                    />
                  </label>
                ))}
                {quizDraft.questions.length > 1 && (
                  <button
                    type="button"
                    className="text-button danger-text"
                    onClick={() => setQuizDraft({
                      ...quizDraft,
                      questions: quizDraft.questions.filter((_, index) => index !== questionIndex),
                    })}
                  >
                    Remove question
                  </button>
                )}
              </fieldset>
            ))}
            <button
              type="button"
              className="button button-secondary small-button"
              onClick={() => setQuizDraft({
                ...quizDraft,
                questions: [...quizDraft.questions, emptyQuestion()],
              })}
            >
              + Add question
            </button>
            <div className="editor-actions">
              <button className="button button-primary small-button" disabled={busy}>
                {quizDraft.id ? "Save quiz" : "Create quiz"}
              </button>
              <button type="button" className="button button-secondary small-button" onClick={() => setQuizDraft(null)}>
                Cancel
              </button>
            </div>
          </form>
        )}

        {role === "LEARNER" && activeQuizId && attempt && (
          <form className="learning-form attempt-form" onSubmit={saveAnswers}>
            <div className="module-heading">
              <strong>{attempt.title}</strong>
              <button type="button" className="text-button" onClick={() => {
                setActiveQuizId(null);
                setAttempt(null);
              }}>
                Close
              </button>
            </div>
            <p className="quiz-meta">
              Due {new Date(attempt.dueAt).toLocaleString()}
            </p>
            {attempt.graded ? (
              <p className="quiz-result" role="status">
                Result: {attempt.score}/{attempt.totalQuestions}. Correct answers are shown below.
              </p>
            ) : Date.now() >= new Date(attempt.dueAt).getTime() ? (
              <div>
                <p className="quiz-result" role="status">
                  The deadline has passed. Your saved answers are being checked.
                </p>
                <button type="button" className="button button-secondary small-button" disabled={busy} onClick={refreshAttempt}>
                  Check results
                </button>
              </div>
            ) : (
              <p className="quiz-meta">Your answers are saved until the deadline. You can revise them before then.</p>
            )}
            {attempt.questions.map((question, index) => (
              <fieldset className="attempt-question" key={question.id}>
                <legend>{index + 1}. {question.prompt}</legend>
                {question.choices.map((choice, choiceIndex) => {
                  const choiceNumber = choiceIndex + 1;
                  const isCorrect = attempt.graded && question.correctChoice === choiceNumber;
                  const wasSelected = question.selectedChoice === choiceNumber;
                  return (
                    <label className={`attempt-choice${isCorrect ? " choice-correct" : ""}${attempt.graded && wasSelected && !isCorrect ? " choice-wrong" : ""}`} key={choiceIndex}>
                      <input
                        type="radio"
                        name={`answer-${question.id}`}
                        value={choiceNumber}
                        checked={answers[question.id] === choiceNumber}
                        disabled={attempt.graded || Date.now() >= new Date(attempt.dueAt).getTime()}
                        onChange={() => setAnswers({ ...answers, [question.id]: choiceNumber })}
                      />
                      <span>{choice}</span>
                      {isCorrect && <strong>Correct answer</strong>}
                      {attempt.graded && wasSelected && !isCorrect && <strong>Your answer</strong>}
                    </label>
                  );
                })}
              </fieldset>
            ))}
            {!attempt.graded && Date.now() < new Date(attempt.dueAt).getTime() && (
              <button className="button button-primary small-button" disabled={busy}>
                Save answers
              </button>
            )}
          </form>
        )}
      </div>
    </section>
  );
}

export default LearningContent;
