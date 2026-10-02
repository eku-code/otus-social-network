package ru.otus.sn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.otus.sn.IntegrationTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiIT extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
    }

    @Test
    void registerShouldCreateUser() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Иван", "Иванов", "1990-05-15", "male")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").isNotEmpty());
    }

    @Test
    void registerShouldFailWithBlankRequiredFields() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("", "", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("first_name, second_name and password are required"));
    }

    @Test
    void registerShouldFailWithInvalidGender() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Иван", "Иванов", null, "other")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("gender must be male or female"));
    }

    @Test
    void registerShouldFailWithInvalidBirthdate() throws Exception {
        String body = "{\"first_name\":\"Иван\",\"second_name\":\"Иванов\"," +
                "\"birthdate\":\"15.05.1990\",\"password\":\"secret\"}";

        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("birthdate must be YYYY-MM-DD"));
    }

    @Test
    void registerShouldFailWithMalformedJson() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("invalid request data"));
    }

    @Test
    void loginShouldReturnTokenForValidCredentials() throws Exception {
        String userId = registerUser();

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + userId + "\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginShouldFailForWrongPassword() throws Exception {
        String userId = registerUser();

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + userId + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("user not found"));
    }

    @Test
    void loginShouldFailForUnknownUser() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + java.util.UUID.randomUUID() + "\",\"password\":\"secret\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("user not found"));
    }

    @Test
    void loginShouldFailWithBlankCredentials() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\" \",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("id and password are required"));
    }

    @Test
    void loginShouldFailWithInvalidUuid() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"not-a-uuid\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("invalid uuid"));
    }

    @Test
    void getUserShouldReturnUserForOwnerToken() throws Exception {
        String userId = registerUser();
        String token = login(userId);

        mockMvc.perform(get("/user/get/" + userId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(userId))
                .andExpect(jsonPath("$.first_name").value("Иван"))
                .andExpect(jsonPath("$.second_name").value("Иванов"))
                .andExpect(jsonPath("$.birthdate").value("1990-05-15"))
                .andExpect(jsonPath("$.city").value("Москва"))
                .andExpect(jsonPath("$.gender").value("male"));
    }

    @Test
    void getUserShouldFailWithoutToken() throws Exception {
        mockMvc.perform(get("/user/get/" + java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("unauthorized"));
    }

    @Test
    void getUserShouldFailWithUnknownToken() throws Exception {
        mockMvc.perform(get("/user/get/" + java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("unauthorized"));
    }

    @Test
    void getUserShouldFailForUnknownUserWithValidToken() throws Exception {
        String token = login(registerUser());

        mockMvc.perform(get("/user/get/" + java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("user not found"));
    }

    @Test
    void getUserShouldFailWithInvalidUserId() throws Exception {
        String token = login(registerUser());

        mockMvc.perform(get("/user/get/not-a-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("invalid uuid"));
    }

    private String registerUser() throws Exception {
        String body = mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Иван", "Иванов", "1990-05-15", "male")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.user_id");
    }

    private String login(String userId) throws Exception {
        String body = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + userId + "\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.token");
    }

    private String registerBody(String firstName, String secondName, String birthdate, String gender) {
        return "{" +
                "\"first_name\":\"" + firstName + "\"," +
                "\"second_name\":\"" + secondName + "\"," +
                (birthdate != null ? "\"birthdate\":\"" + birthdate + "\"," : "") +
                "\"biography\":\"Хобби\"," +
                "\"city\":\"Москва\"," +
                (gender != null ? "\"gender\":\"" + gender + "\"," : "") +
                "\"password\":\"secret\"" +
                "}";
    }
}
