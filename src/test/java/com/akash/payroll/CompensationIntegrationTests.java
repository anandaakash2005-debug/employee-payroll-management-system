package com.akash.payroll;

import com.akash.payroll.dto.*;
import com.akash.payroll.exception.*;
import com.akash.payroll.model.*;
import com.akash.payroll.repository.*;
import com.akash.payroll.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.*;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:compensation;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000")
@AutoConfigureMockMvc
@Import(CompensationIntegrationTests.TimeConfiguration.class)
@WithMockUser(username = "review-admin", roles = "ADMIN")
class CompensationIntegrationTests {
    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>();
        void date(String date) { instant.set(LocalDate.parse(date).atStartOfDay(getZone()).toInstant()); }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Kolkata"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return instant.get(); }
    }
    @Autowired MutableClock clock;
    @Autowired EmployeeRepository employees;
    @Autowired SalaryIncrementRepository increments;
    @Autowired EmployeeBonusRepository bonuses;
    @Autowired PayrollRecordRepository records;
    @Autowired CompensationService compensation;
    @Autowired PayrollPeriodService periods;
    @Autowired PayrollService payroll;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    Long id;

    @BeforeEach void setup() {
        records.deleteAll(); increments.deleteAll(); bonuses.deleteAll(); employees.deleteAll();
        clock.date("2026-10-11");
        id = employees.save(new FullTimeEmployee("Employee", "Engineering", 50000)).getId();
    }
    SalaryIncrementRequest request(String rate, String date) {
        var request = new SalaryIncrementRequest();
        request.setNewRate(new BigDecimal(rate)); request.setEffectiveDate(LocalDate.parse(date)); request.setReason("Review");
        return request;
    }
    SalaryIncrementResponse increment(String rate, String date) { return compensation.incrementSalary(id, request(rate, date)); }
    BigDecimal current() { return CompensationService.currentRate(employees.findById(id).orElseThrow()); }
    PayrollPeriodRequest month(String date) {
        var start = LocalDate.parse(date);
        return new PayrollPeriodRequest(start, start.withDayOfMonth(start.lengthOfMonth()), null);
    }
    EmployeeBonusRequest bonus(String amount) {
        var request = new EmployeeBonusRequest(); request.setAmount(new BigDecimal(amount));
        request.setBonusDate(LocalDate.now(clock)); request.setReason("Project bonus"); return request;
    }

    @Test void todayAppliesAndAudits() {
        var result = increment("60000.00", "2026-10-11");
        assertEquals("APPLIED", result.status()); assertEquals("review-admin", result.createdBy());
        assertNotNull(result.createdAt()); assertNotNull(result.appliedAt());
        assertEquals(new BigDecimal("50000.00"), result.previousRate());
        assertEquals(new BigDecimal("60000.00"), current());
    }
    @Test void pastIsPreservedWhenNoClosedPayroll() {
        var result = increment("60000", "2026-09-01");
        assertEquals(LocalDate.of(2026,9,1), result.effectiveDate()); assertEquals("APPLIED",result.status());
    }
    @Test void futureStaysScheduledAndDoesNotChangeCurrentSalary() {
        var result = increment("60000", "2026-11-01"); compensation.reconcile(id);
        assertEquals("SCHEDULED", result.status()); assertNull(result.appliedAt());
        assertEquals(new BigDecimal("50000.00"), current());
        assertEquals(50000.0, payroll.getById(id).getSalary());
    }
    @Test void dueActivationIsIdempotent() {
        var result = increment("60000", "2026-11-01"); clock.date("2026-11-01");
        compensation.reconcile(id); var appliedAt = increments.findById(result.id()).orElseThrow().getAppliedAt();
        clock.date("2026-11-02"); compensation.reconcile(id);
        assertEquals(new BigDecimal("60000.00"), current()); assertEquals(1, increments.count());
        assertEquals(appliedAt, increments.findById(result.id()).orElseThrow().getAppliedAt());
    }
    @ParameterizedTest @ValueSource(strings={"0", "-1", "60000.001", "10000000000", "60000.000"})
    void invalidAmountsRejected(String amount) {
        assertThrows(IllegalArgumentException.class, () -> increment(amount,"2026-11-01")); assertEquals(0, increments.count());
    }
    @Test void missingEmployeeRejected() {
        assertThrows(EmployeeNotFoundException.class, () -> compensation.incrementSalary(-1L,request("60000","2026-11-01")));
    }
    @Test void duplicateDateAndNonIncreasingRateRejected() {
        increment("60000","2026-11-01");
        assertThrows(CompensationConflictException.class, () -> increment("61000","2026-11-01"));
        assertThrows(CompensationConflictException.class, () -> increment("59000","2026-12-01"));
    }
    @Test void multipleScheduledChangesCanBeInsertedAndActivateInOrder() {
        var later = increment("70000","2026-12-01"); increment("60000","2026-11-01");
        assertEquals(new BigDecimal("60000.00"),increments.findById(later.id()).orElseThrow().getPreviousRate());
        clock.date("2027-01-01"); compensation.reconcile(id);
        assertEquals(new BigDecimal("70000.00"),current());
        assertTrue(compensation.getSalaryHistory(id).stream().allMatch(i -> "APPLIED".equals(i.status())));
    }
    @Test void insertionBeforeAppliedHistoryRejected() {
        increment("60000","2026-09-01");
        assertThrows(CompensationConflictException.class, () -> increment("55000","2026-08-01"));
    }
    @Test void cancellationIsAuditedAndIdempotent() {
        var first = increment("60000","2026-11-01"); var next = increment("70000","2026-12-01");
        assertEquals("CANCELLED",compensation.cancelIncrement(id,first.id()).status());
        assertEquals("review-admin",compensation.cancelIncrement(id,first.id()).cancelledBy());
        assertEquals(new BigDecimal("50000.00"),increments.findById(next.id()).orElseThrow().getPreviousRate());
        assertThrows(CompensationConflictException.class, () -> increment("61000","2026-11-01"));
    }
    @Test void appliedIncrementCannotBeCancelled() {
        var result = increment("60000","2026-10-11");
        assertThrows(CompensationConflictException.class, () -> compensation.cancelIncrement(id,result.id()));
    }
    @Test void inactiveEmployeeCancelsFutureChanges() {
        increment("60000","2026-11-01"); compensation.deactivate(id); clock.date("2026-12-01"); compensation.reconcile(id);
        assertEquals(new BigDecimal("50000.00"),current());
        assertEquals("CANCELLED",compensation.getSalaryHistory(id).get(0).status());
        assertThrows(CompensationConflictException.class, () -> increment("70000","2027-01-01"));
        assertEquals(0,payroll.getPayrollSummary().getTotalEmployees());
    }
    @Test void hourlyIncrementChangesOnlyHourlyRate() {
        id = employees.save(new PartTimeEmployee("Hourly","Support",20,100)).getId();
        var response = increment("125.50","2026-10-11");
        assertEquals("HOURLY",response.payBasis());
        var employee = (PartTimeEmployee) employees.findById(id).orElseThrow();
        assertEquals(20,employee.getHoursWorked()); assertEquals(new BigDecimal("2510.00"),employee.calculatePay());
    }
    @Test void concurrentActivationAppliesOnce() throws Exception {
        increment("60000","2026-11-01"); clock.date("2026-11-01");
        concurrently(() -> { compensation.reconcile(id); return true; });
        assertEquals(1,increments.count()); assertEquals(new BigDecimal("60000.00"),current());
        assertEquals(new BigDecimal("50000.00"),compensation.getSalaryHistory(id).get(0).previousRate());
    }
    @Test void concurrentSubmissionHasOneWinner() throws Exception {
        var results = concurrently(() -> {
            try { increment("60000","2026-11-01"); return true; }
            catch (CompensationConflictException ex) { return false; }
        });
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count()); assertEquals(1,increments.count());
    }
    List<Boolean> concurrently(Callable<Boolean> action) throws Exception {
        var security = SecurityContextHolder.getContext(); var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> task = () -> {
                SecurityContextHolder.setContext(security);
                try { start.await(); return action.call(); }
                finally { SecurityContextHolder.clearContext(); }
            };
            var a=pool.submit(task); var b=pool.submit(task); start.countDown();
            return List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    @Test void schedulerReconcilesOnStartupAndRecurringSweep() {
        increment("60000","2026-11-01"); clock.date("2026-11-01");
        var scheduler = new IncrementScheduler(increments,compensation,clock);
        scheduler.onStartup(); scheduler.reconcileDue();
        assertEquals(new BigDecimal("60000.00"),current()); assertEquals(1,increments.count());
    }
    @Test void readReconcilesDueChangeAfterSleep() {
        increment("60000","2026-11-01"); clock.date("2026-11-01");
        assertEquals(60000.0,payroll.getById(id).getSalary());
    }
    @Test void bonusIsPendingAuditedAndSeparateFromPayroll() {
        var result = compensation.addBonus(id,bonus("5000"));
        assertEquals("PENDING",result.status()); assertEquals("review-admin",result.createdBy()); assertNotNull(result.createdAt());
        assertEquals(new BigDecimal("50000.00"),periods.finalizePeriod(id,month("2026-09-01")).basePay());
    }
    @Test void duplicateBonusCannotBeCreated() {
        compensation.addBonus(id,bonus("5000"));
        assertThrows(CompensationConflictException.class, () -> compensation.addBonus(id,bonus("5000")));
        assertEquals(1,bonuses.count());
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","5.001","10000000000"})
    void invalidBonusRejected(String amount) { assertThrows(IllegalArgumentException.class, () -> compensation.addBonus(id,bonus(amount))); }
    @Test void missingBonusEmployeeAndPastBonusDateRejected() {
        assertThrows(EmployeeNotFoundException.class, () -> compensation.addBonus(-1L,bonus("500")));
        var request=bonus("500");request.setBonusDate(LocalDate.of(2026,10,10));
        assertThrows(IllegalArgumentException.class, () -> compensation.addBonus(id,request));
    }
    @Test void payrollUsesRatesBeforeAndAfterChangeAndProrates() {
        increment("60000","2026-09-16");
        assertEquals(new BigDecimal("50000.00"),periods.finalizePeriod(id,month("2026-08-01")).basePay());
        assertEquals(new BigDecimal("55000.00"),periods.finalizePeriod(id,month("2026-09-01")).basePay());
        clock.date("2026-11-01");
        assertEquals(new BigDecimal("60000.00"),periods.finalizePeriod(id,month("2026-10-01")).basePay());
    }
    @Test void futureChangeCannotAffectEarlierPayroll() {
        increment("60000","2026-11-01");
        assertEquals(new BigDecimal("50000.00"),periods.finalizePeriod(id,month("2026-09-01")).basePay());
    }
    @Test void finalizedPayrollRejectsRetroactiveChangeAndKeepsSnapshot() {
        var result=periods.finalizePeriod(id,month("2026-09-01"));
        assertThrows(CompensationConflictException.class, () -> increment("60000","2026-09-15"));
        assertEquals(result,periods.history(id).get(0));
        assertEquals(new BigDecimal("50000.00"),current());
    }
    @Test void duplicatePayrollRejectedIncludingConcurrentRequests() throws Exception {
        var results = concurrently(() -> {
            try { periods.finalizePeriod(id,month("2026-09-01")); return true; }
            catch (CompensationConflictException ex) { return false; }
        });
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count()); assertEquals(1,records.count());
    }
    @Test void periodBoundariesAndMissingHoursRejected() {
        assertThrows(IllegalArgumentException.class, () -> periods.finalizePeriod(id,month("2026-10-01")));
        assertThrows(IllegalArgumentException.class, () -> periods.finalizePeriod(id,new PayrollPeriodRequest(LocalDate.of(2026,9,2),LocalDate.of(2026,9,30),null)));
        id=employees.save(new PartTimeEmployee("Hourly","Support",100,10.01)).getId();
        assertThrows(IllegalArgumentException.class, () -> periods.finalizePeriod(id,month("2026-09-01")));
    }
    @Test void hourlyPayrollUsesDatedHoursAndRoundsOnce() {
        id=employees.save(new PartTimeEmployee("Hourly","Support",999,10.01)).getId();
        increment("20.01","2026-09-16");
        var hours=new ArrayList<PayrollPeriodRequest.DailyHours>();
        for (int day=1;day<=30;day++) hours.add(new PayrollPeriodRequest.DailyHours(LocalDate.of(2026,9,day),new BigDecimal("0.25")));
        var result=periods.finalizePeriod(id,new PayrollPeriodRequest(LocalDate.of(2026,9,1),LocalDate.of(2026,9,30),hours));
        assertEquals(new BigDecimal("112.58"),result.basePay());
        assertTrue(result.calculationDetails().contains("2026-09-16: rate=20.01"));
    }
    @Test void futureApiPreservesContractAndReturnsScheduledStatus() throws Exception {
        mvc.perform(put("/api/employees/"+id+"/salary-increments").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request("65000","2026-11-01"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.newRate").value(65000)).andExpect(jsonPath("$.previousRate").value(50000));
        mvc.perform(get("/api/employees/"+id+"/salary-increments"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("SCHEDULED"));
    }
    @Test void malformedDateReturnsStructured400() throws Exception {
        mvc.perform(put("/api/employees/"+id+"/salary-increments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"newRate\":60000,\"effectiveDate\":\"not-a-date\",\"reason\":\"Review\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
    @Test void bonusStatusCannotBeInjectedAndNoPaymentOrApprovalRouteExists() throws Exception {
        var payload=mapper.valueToTree(bonus("5000"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)payload).put("status","PAID").put("createdBy","forged-admin");
        mvc.perform(post("/api/employees/"+id+"/bonuses").contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdBy").value("review-admin"));
        mvc.perform(post("/api/employees/"+id+"/bonuses/1/approve")).andExpect(status().isNotFound());
        mvc.perform(put("/api/employees/"+id+"/bonuses/1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAID\"}"))
                .andExpect(status().isNotFound());
    }
    @Test void oversizedJsonIsRejected() throws Exception {
        mvc.perform(put("/api/employees/"+id+"/salary-increments").contentType(MediaType.APPLICATION_JSON).content(" ".repeat(65537)))
                .andExpect(status().isPayloadTooLarge());
    }
    @Test void financialHistoryPreventsEmployeeDeletion() {
        increment("60000","2026-11-01");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> payroll.deleteEmployee(id));
        assertTrue(employees.existsById(id)); assertEquals(1,increments.count());
    }
    @Test void suppliedHoursMustNotDuplicateDaysOrExceed24Hours() {
        id=employees.save(new PartTimeEmployee("Hourly","Support",40,100)).getId();
        var hours=new ArrayList<PayrollPeriodRequest.DailyHours>();
        for(int day=1;day<=30;day++) hours.add(new PayrollPeriodRequest.DailyHours(LocalDate.of(2026,9,1),BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> periods.finalizePeriod(id,new PayrollPeriodRequest(LocalDate.of(2026,9,1),LocalDate.of(2026,9,30),hours)));
        hours.set(0,new PayrollPeriodRequest.DailyHours(LocalDate.of(2026,9,1),new BigDecimal("25")));
        assertThrows(IllegalArgumentException.class, () -> periods.finalizePeriod(id,new PayrollPeriodRequest(LocalDate.of(2026,9,1),LocalDate.of(2026,9,30),hours)));
    }
    @Test void blankReasonReturnsFieldError() throws Exception {
        var request=request("60000","2026-11-01");request.setReason(" ");
        mvc.perform(put("/api/employees/"+id+"/salary-increments").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reason").exists());
    }
    @Test @WithMockUser(roles="USER") void ordinaryUserCannotChangeCompensationOrFinalizePayroll() throws Exception {
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> increment("60000","2026-11-01"));
        mvc.perform(put("/api/employees/"+id+"/salary-increments").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request("60000","2026-11-01"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/employees/"+id+"/bonuses").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(bonus("5000"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/employees/"+id+"/payroll").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(month("2026-09-01"))))
                .andExpect(status().isForbidden());
    }
    @Test @WithAnonymousUser void anonymousRequestRejected() throws Exception {
        mvc.perform(get("/api/employees/"+id+"/salary-increments")).andExpect(status().isUnauthorized());
    }
    @Test void corsOnlyAllowsFrontendAndReadinessChecksDatabase() throws Exception {
        mvc.perform(options("/api/employees").header("Origin","https://anandaakash2005-debug.github.io")
                .header("Access-Control-Request-Method","GET")).andExpect(status().isOk());
        mvc.perform(options("/api/employees").header("Origin","https://untrusted.example")
                .header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden());
        mvc.perform(get("/ready")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
