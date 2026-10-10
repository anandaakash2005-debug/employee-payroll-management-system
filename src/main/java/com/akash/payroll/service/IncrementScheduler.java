package com.akash.payroll.service;

import com.akash.payroll.repository.SalaryIncrementRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "payroll.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class IncrementScheduler {
    private final SalaryIncrementRepository increments;
    private final CompensationService compensation;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() { reconcileDue(); }

    @Scheduled(fixedDelayString = "${payroll.scheduling.delay-ms:60000}", initialDelayString = "${payroll.scheduling.delay-ms:60000}")
    public void reconcileDue() {
        try {
            long after = 0;
            while (true) {
                var ids = increments.findDueEmployeeIds(LocalDate.now(clock), after,
                        com.akash.payroll.model.SalaryIncrement.Status.SCHEDULED, PageRequest.of(0, 100));
                if (ids.isEmpty()) return;
                for (Long id : ids) {
                    try { compensation.reconcile(id); }
                    catch (RuntimeException exception) {
                        log.error("Increment reconciliation failed for employee {}; next sweep will retry ({})", id, exception.getClass().getSimpleName());
                    }
                }
                after = ids.get(ids.size() - 1);
            }
        } catch (RuntimeException exception) {
            log.error("Increment sweep unavailable; next sweep will retry ({})", exception.getClass().getSimpleName());
        }
    }
}
