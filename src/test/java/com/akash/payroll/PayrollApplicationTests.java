package com.akash.payroll;

import com.akash.payroll.dto.FullTimeEmployeeRequest;
import com.akash.payroll.dto.PartTimeEmployeeRequest;
import com.akash.payroll.repository.EmployeeRepository;
import com.akash.payroll.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@WithMockUser(username = "admin", roles = "ADMIN")
class PayrollApplicationTests {

    @Autowired MockMvc         mockMvc;
    @Autowired ObjectMapper    mapper;
    @Autowired EmployeeRepository repo;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TestRestTemplate testRestTemplate;
    @LocalServerPort int port;

    @BeforeEach
    void cleanUp() {
        repo.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterAndLoginWithHashedPassword() throws Exception {
        Map<String, String> request = Map.of(
                "username", "payroll-test-user",
                "password", "test-password-123");
        String requestJson = mapper.writeValueAsString(request);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("payroll-test-user"));

        var savedUser = userRepository.findByUsername("payroll-test-user").orElseThrow();
        assertTrue(passwordEncoder.matches("test-password-123", savedUser.getPassword()));
        assertNotEquals("test-password-123", savedUser.getPassword());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void shouldStartEmbeddedServerAndProtectApi() {
        var response = testRestTemplate.getForEntity(
                "http://localhost:" + port + "/api/employees", String.class);
        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    @WithAnonymousUser
    void shouldRejectInvalidJwtForProtectedEmployeeApi() throws Exception {
        mockMvc.perform(get("/api/employees").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST Tests ────────────────────────────────────────────────────────────

    @Test @Order(1)
    void shouldAddFullTimeEmployee() throws Exception {
        FullTimeEmployeeRequest req = new FullTimeEmployeeRequest();
        req.setName("Abhishek Kumar");
        req.setDepartment("Engineering");
        req.setMonthlySalary(75000.0);

        mockMvc.perform(post("/api/employees/fulltime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Abhishek Kumar"))
                .andExpect(jsonPath("$.employeeType").value("FULL_TIME"))
                .andExpect(jsonPath("$.salary").value(75000.0));
    }

    @Test @Order(2)
    void shouldAddPartTimeEmployee() throws Exception {
        PartTimeEmployeeRequest req = new PartTimeEmployeeRequest();
        req.setName("Rahul Sharma");
        req.setDepartment("Marketing");
        req.setHoursWorked(40);
        req.setHourlyRate(500.0);

        mockMvc.perform(post("/api/employees/parttime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employeeType").value("PART_TIME"))
                .andExpect(jsonPath("$.salary").value(20000.0));  // 40 × 500
    }

    @Test @Order(3)
    void shouldReturn400WhenNameBlank() throws Exception {
        FullTimeEmployeeRequest req = new FullTimeEmployeeRequest();
        req.setName("");   // invalid
        req.setDepartment("Engineering");
        req.setMonthlySalary(50000.0);

        mockMvc.perform(post("/api/employees/fulltime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Name is required"));
    }

    @Test
    void shouldSearchEmployeesAndCalculatePayrollSummary() throws Exception {
        FullTimeEmployeeRequest fullTime = new FullTimeEmployeeRequest();
        fullTime.setName("Searchable Employee");
        fullTime.setDepartment("Engineering");
        fullTime.setMonthlySalary(75000.0);
        mockMvc.perform(post("/api/employees/fulltime")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(fullTime)));

        PartTimeEmployeeRequest partTime = new PartTimeEmployeeRequest();
        partTime.setName("Part Time Employee");
        partTime.setDepartment("Support");
        partTime.setHoursWorked(40);
        partTime.setHourlyRate(500.0);
        mockMvc.perform(post("/api/employees/parttime")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(partTime)));

        FullTimeEmployeeRequest priya = new FullTimeEmployeeRequest();
        priya.setName("Priya Gupta");
        priya.setDepartment("HR");
        priya.setMonthlySalary(65000.0);
        mockMvc.perform(post("/api/employees/fulltime")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(priya)));

        FullTimeEmployeeRequest sneha = new FullTimeEmployeeRequest();
        sneha.setName("Sneha Patel");
        sneha.setDepartment("Engineering");
        sneha.setMonthlySalary(80000.0);
        mockMvc.perform(post("/api/employees/fulltime")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(sneha)));

        PartTimeEmployeeRequest ananya = new PartTimeEmployeeRequest();
        ananya.setName("Ananya Singh");
        ananya.setDepartment("Support");
        ananya.setHoursWorked(60);
        ananya.setHourlyRate(350.0);
        mockMvc.perform(post("/api/employees/parttime")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(ananya)));

        mockMvc.perform(get("/api/employees/search").param("name", "searchable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Searchable Employee"));

        mockMvc.perform(get("/api/employees/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees").value(5))
                .andExpect(jsonPath("$.fullTimeCount").value(3))
                .andExpect(jsonPath("$.partTimeCount").value(2))
                .andExpect(jsonPath("$.totalPayroll").value(261000.0));
    }

    @Test
    void shouldUpdateEmployeeAndRejectWrongEmployeeType() throws Exception {
        FullTimeEmployeeRequest fullTime = new FullTimeEmployeeRequest();
        fullTime.setName("Before Update");
        fullTime.setDepartment("Engineering");
        fullTime.setMonthlySalary(60000.0);
        String created = mockMvc.perform(post("/api/employees/fulltime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(fullTime)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).get("id").asLong();

        fullTime.setName("After Update");
        fullTime.setMonthlySalary(70000.0);
        mockMvc.perform(put("/api/employees/" + id + "/fulltime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(fullTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("After Update"))
                .andExpect(jsonPath("$.salary").value(70000.0));

        PartTimeEmployeeRequest partTime = new PartTimeEmployeeRequest();
        partTime.setName("Part Time Employee");
        partTime.setDepartment("Support");
        partTime.setHoursWorked(20);
        partTime.setHourlyRate(250.0);
        String partTimeCreated = mockMvc.perform(post("/api/employees/parttime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(partTime)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long partTimeId = mapper.readTree(partTimeCreated).get("id").asLong();

        mockMvc.perform(put("/api/employees/" + partTimeId + "/fulltime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(fullTime)))
                .andExpect(status().isBadRequest());
    }

    // ── GET Tests ─────────────────────────────────────────────────────────────

    @Test @Order(4)
    void shouldReturn404ForNonExistentEmployee() throws Exception {
        mockMvc.perform(get("/api/employees/999"))
                .andExpect(status().isNotFound());
    }

    @Test @Order(5)
    void shouldGetAllEmployees() throws Exception {
        // Seed
        FullTimeEmployeeRequest req = new FullTimeEmployeeRequest();
        req.setName("Test User"); req.setDepartment("IT"); req.setMonthlySalary(60000.0);
        mockMvc.perform(post("/api/employees/fulltime")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── DELETE Tests ──────────────────────────────────────────────────────────

    @Test @Order(6)
    void shouldDeleteEmployee() throws Exception {
        FullTimeEmployeeRequest req = new FullTimeEmployeeRequest();
        req.setName("Delete Me"); req.setDepartment("HR"); req.setMonthlySalary(40000.0);

        String body = mockMvc.perform(post("/api/employees/fulltime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andReturn().getResponse().getContentAsString();

        Long id = mapper.readTree(body).get("id").asLong();

        mockMvc.perform(delete("/api/employees/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/employees/" + id))
                .andExpect(status().isNotFound());
    }
}