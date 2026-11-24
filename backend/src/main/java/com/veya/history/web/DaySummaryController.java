package com.veya.history.web;

import com.veya.history.application.DaySummaryService;
import com.veya.history.application.DaySummaryService.DaySummary;
import com.veya.identity.api.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** operationId getDaySummary (US-20, US-07). */
@RestController
public class DaySummaryController {

    private final DaySummaryService summaries;

    public DaySummaryController(DaySummaryService summaries) {
        this.summaries = summaries;
    }

    @GetMapping("/api/v1/days/{date}/summary")
    public DaySummary getDaySummary(CurrentUser user,
                                    @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return summaries.summary(user.id(), date);
    }
}
