package com.it342g02.learnon.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.it342g02.learnon.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:learnon-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AuthenticationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registrationLoginSessionAndLogoutWorkTogether() throws Exception {
        JsonNode csrf = csrfToken(null);
        String email = "integration@example.com";
        String password = "integration-password";

        mockMvc.perform(post("/api/users")
                        .cookie(csrfCookie(csrf))
                        .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Integration",
                                  "lastName": "Tester",
                                  "email": "integration@example.com",
                                  "password": "integration-password"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(password))));

        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .matches(password, userRepository.findByEmail(email).orElseThrow().getPassword()));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie(csrf))
                        .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"authenticated\":true}"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        JsonNode refreshedCsrf = csrfToken(session);
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"email\":\"integration@example.com\"}"));

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(csrfCookie(refreshedCsrf))
                        .header(refreshedCsrf.path("headerName").asText(),
                                refreshedCsrf.path("token").asText()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherCanManageOwnedCoursesAndLearnerEnrollmentProgress() throws Exception {
        String teacherEmail = "teacher@example.com";
        String learnerEmail = "learner@example.com";
        String password = "integration-password";
        register(teacherEmail, password, "Teacher", "Account");
        register(learnerEmail, password, "Learner", "Account");

        MockHttpSession teacherSession = login(teacherEmail, password);
        JsonNode teacherCsrf = csrfToken(teacherSession);
        mockMvc.perform(put("/api/auth/role")
                        .session(teacherSession)
                        .cookie(csrfCookie(teacherCsrf))
                        .header(teacherCsrf.path("headerName").asText(),
                                teacherCsrf.path("token").asText())
                        .contentType(APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"role\":\"TEACHER\"}"));

        teacherCsrf = csrfToken(teacherSession);
        MvcResult courseResult = mockMvc.perform(post("/api/courses")
                        .session(teacherSession)
                        .cookie(csrfCookie(teacherCsrf))
                        .header(teacherCsrf.path("headerName").asText(),
                                teacherCsrf.path("token").asText())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Owned course",
                                  "description": "A course managed by its teacher.",
                                  "published": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long courseId = objectMapper.readTree(
                courseResult.getResponse().getContentAsString()).path("id").asLong();

        teacherCsrf = csrfToken(teacherSession);
        mockMvc.perform(post("/api/courses/{courseId}/learners", courseId)
                        .session(teacherSession)
                        .cookie(csrfCookie(teacherCsrf))
                        .header(teacherCsrf.path("headerName").asText(),
                                teacherCsrf.path("token").asText())
                        .param("email", learnerEmail))
                .andExpect(status().isCreated());

        MockHttpSession learnerSession = login(learnerEmail, password);
        JsonNode learnerCsrf = csrfToken(learnerSession);
        long learnerId = userRepository.findByEmail(learnerEmail).orElseThrow().getId();
        mockMvc.perform(put("/api/progress")
                        .session(learnerSession)
                        .cookie(csrfCookie(learnerCsrf))
                        .header(learnerCsrf.path("headerName").asText(),
                                learnerCsrf.path("token").asText())
                        .param("userId", Long.toString(learnerId))
                        .param("courseId", Long.toString(courseId))
                        .param("completedLessons", "2")
                        .param("totalLessons", "5"))
                .andExpect(status().isOk());

        teacherCsrf = csrfToken(teacherSession);
        mockMvc.perform(get("/api/enrollments/course/{courseId}", courseId)
                        .session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"user\":{\"email\":\"learner@example.com\"}}]"));
        mockMvc.perform(get("/api/progress/course/{courseId}", courseId)
                        .session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"completionPercentage\":40.0}]"));

        learnerCsrf = csrfToken(learnerSession);
        mockMvc.perform(post("/api/courses")
                        .session(learnerSession)
                        .cookie(csrfCookie(learnerCsrf))
                        .header(learnerCsrf.path("headerName").asText(),
                                learnerCsrf.path("token").asText())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Unauthorized course",
                                  "description": "Learners cannot create courses.",
                                  "published": true
                                }
                                """))
                .andExpect(status().isForbidden());

        register("other-teacher@example.com", password, "Other", "Teacher");
        MockHttpSession otherTeacherSession = login("other-teacher@example.com", password);
        JsonNode otherTeacherCsrf = csrfToken(otherTeacherSession);
        mockMvc.perform(put("/api/auth/role")
                        .session(otherTeacherSession)
                        .cookie(csrfCookie(otherTeacherCsrf))
                        .header(otherTeacherCsrf.path("headerName").asText(),
                                otherTeacherCsrf.path("token").asText())
                        .contentType(APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\"}"))
                .andExpect(status().isOk());
        otherTeacherCsrf = csrfToken(otherTeacherSession);
        mockMvc.perform(put("/api/courses/{courseId}", courseId)
                        .session(otherTeacherSession)
                        .cookie(csrfCookie(otherTeacherCsrf))
                        .header(otherTeacherCsrf.path("headerName").asText(),
                                otherTeacherCsrf.path("token").asText())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Changed by another teacher",
                                  "description": "This update should be rejected.",
                                  "published": true
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/courses/{courseId}", courseId)
                        .session(teacherSession)
                        .cookie(csrfCookie(teacherCsrf))
                        .header(teacherCsrf.path("headerName").asText(),
                                teacherCsrf.path("token").asText()))
                .andExpect(status().isConflict());

        MvcResult enrollmentResult = mockMvc.perform(
                        get("/api/enrollments/course/{courseId}", courseId)
                                .session(teacherSession))
                .andExpect(status().isOk())
                .andReturn();
        long enrollmentId = objectMapper.readTree(
                enrollmentResult.getResponse().getContentAsString())
                .get(0).path("id").asLong();
        mockMvc.perform(delete("/api/enrollments/{enrollmentId}", enrollmentId)
                        .session(teacherSession)
                        .cookie(csrfCookie(teacherCsrf))
                        .header(teacherCsrf.path("headerName").asText(),
                                teacherCsrf.path("token").asText()))
                .andExpect(status().isNoContent());

        teacherCsrf = csrfToken(teacherSession);
        mockMvc.perform(get("/api/progress/course/{courseId}", courseId)
                        .session(teacherSession))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(delete("/api/courses/{courseId}", courseId)
                        .session(teacherSession)
                        .cookie(csrfCookie(teacherCsrf))
                        .header(teacherCsrf.path("headerName").asText(),
                                teacherCsrf.path("token").asText()))
                .andExpect(status().isNoContent());
    }

    private void register(String email, String password, String firstName, String lastName)
            throws Exception {
        JsonNode csrf = csrfToken(null);
        mockMvc.perform(post("/api/users")
                        .cookie(csrfCookie(csrf))
                        .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "firstName", firstName,
                                "lastName", lastName,
                                "email", email,
                                "password", password))))
                .andExpect(status().isCreated());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        JsonNode csrf = csrfToken(null);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie(csrf))
                        .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private JsonNode csrfToken(MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/auth/csrf");
        if (session != null) {
            request.session(session);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Cookie csrfCookie(JsonNode csrf) {
        return new Cookie("XSRF-TOKEN", csrf.path("token").asText());
    }
}
