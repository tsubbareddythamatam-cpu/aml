package org.aml.service;

import lombok.RequiredArgsConstructor;
import org.aml.dto.CustomerDto;
import org.aml.dto.ScreeningHitDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class InsightsDashboardService {

    private final ComplianceService complianceService;

    public Dashboard create(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be on or before the end date.");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 365) {
            throw new IllegalArgumentException("Choose a date range of one year or less.");
        }

        List<CustomerDto> reports = complianceService.fetchAll().stream()
                .filter(report -> report.getRegisteredAt() != null)
                .filter(report -> {
                    LocalDate registeredDate = report.getRegisteredAt().toLocalDate();
                    return !registeredDate.isBefore(startDate) && !registeredDate.isAfter(endDate);
                })
                .sorted(Comparator.comparing(CustomerDto::getRegisteredAt).reversed())
                .toList();

        List<ActivityPoint> activity = new ArrayList<>();
        Map<LocalDate, long[]> activityByDay = new TreeMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            activityByDay.put(date, new long[2]);
        }
        for (CustomerDto report : reports) {
            LocalDate registeredDate = report.getRegisteredAt().toLocalDate();
            long[] counts = activityByDay.get(registeredDate);
            if (counts != null) {
                counts[0]++;
            }
            if (report.getPassportVerifiedAt() != null) {
                LocalDate verifiedDate = report.getPassportVerifiedAt().toLocalDate();
                long[] verifiedCounts = activityByDay.get(verifiedDate);
                if (verifiedCounts != null) {
                    verifiedCounts[1]++;
                }
            }
        }
        activityByDay.forEach((date, counts) ->
                activity.add(new ActivityPoint(date.toString(), counts[0], counts[1])));

        Map<String, Long> alertTypes = new LinkedHashMap<>();
        alertTypes.put("Name screening", 0L);
        alertTypes.put("Document verification", 0L);
        alertTypes.put("Risk rating", 0L);
        List<AlertRow> alerts = new ArrayList<>();
        long withAlerts = 0;
        for (CustomerDto report : reports) {
            boolean hasAlert = false;
            hasAlert |= addAlert(report, report.getNameScreeningHit(), "Name screening", alertTypes, alerts);
            boolean documentIssue = "FAILED".equals(report.getPassportVerificationStatus())
                    || "FAILED".equals(report.getPassportImageQualityStatus());
            hasAlert |= addAlert(report, documentIssue,
                    "Document verification", alertTypes, alerts);
            hasAlert |= addAlert(report, report.getRiskRatingHit(), "Risk rating", alertTypes, alerts);
            if (hasAlert) {
                withAlerts++;
            }
        }

        Map<String, Long> statuses = new LinkedHashMap<>();
        Map<String, Long> categories = new LinkedHashMap<>();
        for (CustomerDto report : reports) {
            statuses.merge(displayValue(report.getStatus(), "Unknown"), 1L, Long::sum);
            categories.merge("COMPANY".equalsIgnoreCase(report.getCategory()) ? "Company" : "Individual",
                    1L, Long::sum);
        }

        LocalDate agingDate = endDate.isAfter(LocalDate.now()) ? LocalDate.now() : endDate;
        long[] agingCounts = new long[4];
        for (AlertRow alert : alerts) {
            long age = Math.max(0, ChronoUnit.DAYS.between(alert.date(), agingDate));
            if (age <= 30) {
                agingCounts[0]++;
            } else if (age <= 60) {
                agingCounts[1]++;
            } else if (age <= 90) {
                agingCounts[2]++;
            } else {
                agingCounts[3]++;
            }
        }
        List<ChartItem> aging = List.of(
                new ChartItem("0–30 days", agingCounts[0]),
                new ChartItem("31–60 days", agingCounts[1]),
                new ChartItem("61–90 days", agingCounts[2]),
                new ChartItem("90+ days", agingCounts[3]));

        return new Dashboard(startDate, endDate, reports.size(),
                reports.stream().filter(report -> !"VERIFIED".equals(report.getPassportVerificationStatus())).count(),
                reports.stream().filter(report -> "APPROVED".equals(report.getStatus())
                        || "ACTIVE".equals(report.getStatus())).count(),
                reports.stream().filter(report -> "REJECTED".equals(report.getStatus())).count(),
                withAlerts, reports.size() - withAlerts, activity,
                toChartItems(alertTypes), toChartItems(statuses), toChartItems(categories), aging,
                alerts.stream().sorted(Comparator.comparing(AlertRow::date).reversed()).toList(), reports);
    }

    private boolean addAlert(CustomerDto report, Boolean triggered, String type,
                             Map<String, Long> alertTypes, List<AlertRow> alerts) {
        if (!Boolean.TRUE.equals(triggered)) {
            return false;
        }
        alertTypes.merge(type, 1L, Long::sum);
        Integer score = report.getScreeningHits() == null ? null : report.getScreeningHits().stream()
                .filter(hit -> hitMatchesType(hit, type))
                .map(ScreeningHitDto::getScore)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(null);
        alerts.add(new AlertRow(report.getAmlId(), displayValue(report.getFullName(), "Unnamed customer"),
                type, displayValue(report.getStatus(), "Unknown"), report.getRegisteredAt().toLocalDate(), score));
        return true;
    }

    private boolean hitMatchesType(ScreeningHitDto hit, String type) {
        if (hit == null) {
            return false;
        }
        String category = hit.getCategory() == null ? "" : hit.getCategory().toLowerCase();
        return switch (type) {
            case "Name screening" -> category.contains("name") || category.contains("sanction") || category.contains("pep");
            case "Document verification" -> category.contains("document") || category.contains("passport");
            case "Risk rating" -> category.contains("risk");
            default -> false;
        };
    }

    private List<ChartItem> toChartItems(Map<String, Long> values) {
        return values.entrySet().stream().map(entry -> new ChartItem(entry.getKey(), entry.getValue())).toList();
    }

    private String displayValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Dashboard(LocalDate startDate, LocalDate endDate, long onboarded, long verificationPending,
                            long approved, long rejected, long withAlerts, long withoutAlerts,
                            List<ActivityPoint> activity, List<ChartItem> alertTypes, List<ChartItem> statuses,
                            List<ChartItem> categories, List<ChartItem> aging, List<AlertRow> alerts,
                            List<CustomerDto> reports) {
    }

    public record ActivityPoint(String date, long onboarded, long passportsVerified) {
    }

    public record ChartItem(String label, long value) {
    }

    public record AlertRow(String amlId, String name, String type, String status, LocalDate date, Integer score) {
    }
}
