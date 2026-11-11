# LearnOn – Four Entity Spring Boot Backend

## Included scope

This project implements only these four entities:

- User
- Course
- Enrollment
- Progress

Each entity has a Repository, a single Service class, and a Controller. The
React + Vite frontend provides registration, sign-in, and sign-out backed by
Spring Security sessions.

## Package structure

All package names are lowercase:

```text
com.it342g02.learnon
├── controller
├── entity
├── repository
├── config
├── service
└── LearnonApplication.java
```

There are no DTO packages. Security configuration is in `com.it342g02.learnon.config`.

## Domain relationships

- User 1-to-many Enrollment; Course 1-to-many Enrollment.
- User 1-to-many Progress; Course 1-to-many Progress.
- Each course belongs to the teacher who created it.
- A user can enroll in a course only once.
- A user has one Progress record per course.
- Signed-in users can switch between learner and teacher modes.
- Teachers can manage only their own courses and enrolled learners.
- A course cannot be deleted while it has enrollments or progress. Removing a
  learner's enrollment also removes their progress for that course.

### Progress scope decision

Your original diagram connects LessonProgress to Lesson. Since Lesson is outside the four-entity
scope you selected, this project uses course-level Progress with completed lesson count,
total lesson count, and completion percentage. Progress can later be changed to reference
Lesson when that entity is added.

## Requirements

- Java 17 or later
- Maven
- MySQL
- Node.js 18 or later and npm

## Configure the database

Open `src/main/resources/application.properties`.

Defaults are:
- Database: `learnon_db`
- Username: `root`
- Password: blank

You can change the values directly or set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`
environment variables.

## Run in development

Start the backend from the project root (the folder containing `pom.xml`):

```bash
mvn spring-boot:run
```

If your terminal is already in `frontend`, run the backend with the root Maven
project explicitly selected:

```powershell
mvn -f ..\pom.xml spring-boot:run
```

Or use the frontend's npm shortcut from that folder:

```powershell
npm run backend
```

In another terminal, start the frontend:

```powershell
cd frontend
npm install
npm run dev
```

Open the Vite URL shown in the terminal (normally `http://127.0.0.1:5173`).
The frontend proxies `/api` requests to Spring Boot at `http://localhost:8080`.
Registration, sign-in, and sign-out use a server-side session and CSRF tokens.

The `No plugin found for prefix 'spring-boot'` error usually means Maven was
started from `frontend`, where there is no Maven project. Run Maven from the
project root or use `mvn -f ..\pom.xml spring-boot:run` while in `frontend`.

## Build a single Spring Boot JAR

Build the frontend first, then package the backend:

```bash
cd frontend
npm install
npm run build
cd ..
mvn package
```

The frontend build is written to Spring Boot's static resources. Run the JAR
with `java -jar target/learnon-0.0.1-SNAPSHOT.jar` and open
`http://localhost:8080`.

## Endpoints

### Users
- `POST /api/users`
- `GET /api/auth/csrf`
- `POST /api/auth/login` (email and password form fields)
- `GET /api/auth/me`
- `PUT /api/auth/role` (`{"role":"LEARNER"}` or `{"role":"TEACHER"}`)
- `POST /api/auth/logout`

### Courses
- `POST /api/courses` (teacher; creates a course owned by the signed-in teacher)
- `GET /api/courses` (published courses for learners; owned courses for teachers)
- `GET /api/courses/owned` (teacher)
- `GET /api/courses/published`
- `GET /api/courses/{id}` (published for learners; owned by teacher)
- `PUT /api/courses/{id}` (own course only)
- `DELETE /api/courses/{id}` (own course only; blocked while enrollments/progress exist)
- `POST /api/courses/{courseId}/learners?email=learner@example.com` (teacher)

### Enrollments
- `POST /api/enrollments?userId=1&courseId=1` (learner enrolls self)
- `GET /api/enrollments/user/{userId}` (learner's own enrollments)
- `GET /api/enrollments/course/{courseId}` (own course's learners, teacher)
- `DELETE /api/enrollments/{enrollmentId}` (learner leaves or teacher removes learner)

### Progress
- `PUT /api/progress?userId=1&courseId=1&completedLessons=3&totalLessons=10`
- `GET /api/progress/user/{userId}/course/{courseId}`
- `GET /api/progress/user/{userId}`
- `GET /api/progress/course/{courseId}`

State-changing requests must include the CSRF token returned by
`GET /api/auth/csrf` in the response's named header.

Existing courses created before teacher ownership was added have no owner and
cannot be managed by a teacher. New courses created by a teacher are owned
automatically.

## Example request bodies

### Create user

```json
{
  "firstName": "Alex",
  "lastName": "Santos",
  "email": "alex@example.com",
  "password": "sample-password"
}
```

The role is assigned as `LEARNER` by the service. Passwords are BCrypt-hashed
before storage and omitted from JSON responses.

### Create course

```json
{
  "title": "Java Fundamentals",
  "description": "An introduction to Java.",
  "published": true
}
```

## Password storage

Registration hashes passwords with BCrypt, and state-changing requests require
CSRF tokens. Existing accounts created before password hashing was added have
plaintext passwords in the database and cannot sign in until their passwords
are reset or the development database is recreated. Do not use real credentials
in a learning project without a production security review.
