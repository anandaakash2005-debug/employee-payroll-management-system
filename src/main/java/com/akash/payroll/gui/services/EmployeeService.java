package com.akash.payroll.gui.services;

import com.akash.payroll.dto.EmployeeResponse;
import com.akash.payroll.dto.PayrollSummaryResponse;
import com.akash.payroll.gui.ApiClient;
import com.akash.payroll.gui.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class EmployeeService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<EmployeeResponse> getAllEmployees() throws ApiException {
        String json = ApiClient.get("/api/employees");
        try {
            return MAPPER.readValue(json, new TypeReference<List<EmployeeResponse>>() {});
        } catch (Exception e) {
            throw new ApiException(500, "Unable to parse employee list");
        }
    }

    public List<EmployeeResponse> getFullTimeEmployees() throws ApiException {
        String json = ApiClient.get("/api/employees/fulltime");
        try {
            return MAPPER.readValue(json, new TypeReference<List<EmployeeResponse>>() {});
        } catch (Exception e) {
            throw new ApiException(500, "Unable to parse full-time employees");
        }
    }

    public List<EmployeeResponse> getPartTimeEmployees() throws ApiException {
        String json = ApiClient.get("/api/employees/parttime");
        try {
            return MAPPER.readValue(json, new TypeReference<List<EmployeeResponse>>() {});
        } catch (Exception e) {
            throw new ApiException(500, "Unable to parse part-time employees");
        }
    }

    public List<EmployeeResponse> searchByName(String query) throws ApiException {
        String json = ApiClient.get("/api/employees/search?name=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8));
        try {
            return MAPPER.readValue(json, new TypeReference<List<EmployeeResponse>>() {});
        } catch (Exception e) {
            throw new ApiException(500, "Unable to parse search results");
        }
    }

    public List<EmployeeResponse> searchByDepartment(String department) throws ApiException {
        String json = ApiClient.get("/api/employees/department?name=" + java.net.URLEncoder.encode(department, java.nio.charset.StandardCharsets.UTF_8));
        try {
            return MAPPER.readValue(json, new TypeReference<List<EmployeeResponse>>() {});
        } catch (Exception e) {
            throw new ApiException(500, "Unable to parse department results");
        }
    }

    public PayrollSummaryResponse getSummary() throws ApiException {
        String json = ApiClient.get("/api/employees/summary");
        try {
            return MAPPER.readValue(json, PayrollSummaryResponse.class);
        } catch (Exception e) {
            throw new ApiException(500, "Unable to parse payroll summary");
        }
    }

    public EmployeeResponse addFullTimeEmployee(String name, String department, double monthlySalary) throws ApiException {
        String json = ApiClient.post("/api/employees/fulltime", new java.util.HashMap<String, Object>() {{
            put("name", name);
            put("department", department);
            put("monthlySalary", monthlySalary);
        }});
        try {
            return MAPPER.readValue(json, EmployeeResponse.class);
        } catch (Exception e) {
            throw new ApiException(500, "Unable to parse created full-time employee");
        }
    }

    public EmployeeResponse addPartTimeEmployee(String name, String department, int hoursWorked, double hourlyRate) throws ApiException {
        String json = ApiClient.post("/api/employees/parttime", new java.util.HashMap<String, Object>() {{
            put("name", name);
            put("department", department);
            put("hoursWorked", hoursWorked);
            put("hourlyRate", hourlyRate);
        }});
        try {
            return MAPPER.readValue(json, EmployeeResponse.class);
        } catch (Exception e) {
            throw new ApiException(500, "Unable to parse created part-time employee");
        }
    }

    public EmployeeResponse updateFullTimeEmployee(Long id, String name, String department, double monthlySalary) throws ApiException {
        String json = ApiClient.put("/api/employees/" + id + "/fulltime", new java.util.HashMap<String, Object>() {{
            put("name", name);
            put("department", department);
            put("monthlySalary", monthlySalary);
        }});
        try {
            return MAPPER.readValue(json, EmployeeResponse.class);
        } catch (Exception e) {
            throw new ApiException(500, "Unable to parse updated full-time employee");
        }
    }

    public EmployeeResponse updatePartTimeEmployee(Long id, String name, String department, int hoursWorked, double hourlyRate) throws ApiException {
        String json = ApiClient.put("/api/employees/" + id + "/parttime", new java.util.HashMap<String, Object>() {{
            put("name", name);
            put("department", department);
            put("hoursWorked", hoursWorked);
            put("hourlyRate", hourlyRate);
        }});
        try {
            return MAPPER.readValue(json, EmployeeResponse.class);
        } catch (Exception e) {
            throw new ApiException(500, "Unable to parse updated part-time employee");
        }
    }

    public void deleteEmployee(Long id) throws ApiException {
        ApiClient.delete("/api/employees/" + id);
    }
}
