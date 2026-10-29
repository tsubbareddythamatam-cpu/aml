package org.aml.controller;

import lombok.RequiredArgsConstructor;
import org.aml.dto.CustomerDto;
import org.aml.dto.AuditLogDto;
import org.aml.service.ComplianceService;
import org.aml.service.InsightsDashboardService;
import org.aml.service.InsightsExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/portal")
@RequiredArgsConstructor
public class PortalModuleController {

    private static final Map<String, ModuleInfo> MODULES = Map.of(
            "insights", new ModuleInfo("Insights", "A live overview of customer onboarding and compliance progress.",
                    "Customer activity is calculated from the records currently in AML."),
            "configurations", new ModuleInfo("Configurations", "Workspace access and service settings",
                    "Review how workspace access and application settings are managed. Sensitive technical settings are not editable in this portal."),
            "packages", new ModuleInfo("Packages", "AML workspace capabilities",
                    "Explore the onboarding, verification, compliance, and reporting capabilities available in this workspace. Subscription plans are not configured here."),
            "system-logs", new ModuleInfo("System Logs", "Compliance audit trail",
                    "Profile changes and passport reviews are recorded in each customer’s audit history."),
            "developer-tools", new ModuleInfo("Developer Tools", "API documentation",
                    "Explore the AML API using the secured OpenAPI documentation."),
            "help", new ModuleInfo("Help Desk", "AML support",
                    "For account or workflow support, contact your AML platform administrator."),
            "user-guide", new ModuleInfo("User Guide", "Working with AML",
                    "Start by registering an individual or company. Upload and review the passport, then complete the compliance form and update its process status."),
            "deep-link", new ModuleInfo("Deep Link Onboarding", "Guided onboarding",
                    "Choose an individual or company onboarding path to create a customer profile.")
    );

    private final ComplianceService complianceService;
    private final InsightsDashboardService insightsDashboardService;
    private final InsightsExportService insightsExportService;

    @ModelAttribute("currentUser")
    public String currentUser(Authentication authentication) {
        return authentication == null ? "" : authentication.getName();
    }

    @GetMapping("/insights")
    public String insights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {
        LocalDate effectiveEnd = endDate == null ? LocalDate.now() : endDate;
        LocalDate effectiveStart = startDate == null ? effectiveEnd.minusDays(29) : startDate;
        InsightsDashboardService.Dashboard dashboard;
        try {
            dashboard = insightsDashboardService.create(effectiveStart, effectiveEnd);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("dateError", exception.getMessage());
            effectiveEnd = LocalDate.now();
            effectiveStart = effectiveEnd.minusDays(29);
            dashboard = insightsDashboardService.create(effectiveStart, effectiveEnd);
        }
        addModuleAttributes(model, "insights");
        model.addAttribute("insights", dashboard);
        model.addAttribute("startDate", effectiveStart);
        model.addAttribute("endDate", effectiveEnd);
        return "portal/module";
    }

    @GetMapping("/insights/export")
    public ResponseEntity<byte[]> exportInsights(
            @RequestParam String format,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws java.io.IOException {
        if ("excel".equalsIgnoreCase(format)) {
            InsightsDashboardService.Dashboard dashboard = createInsightsDashboard(startDate, endDate);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"aml-insights-" + startDate + "-to-" + endDate + ".xlsx\"")
                    .body(insightsExportService.exportExcel(dashboard));
        }
        if ("pdf".equalsIgnoreCase(format)) {
            InsightsDashboardService.Dashboard dashboard = createInsightsDashboard(startDate, endDate);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"aml-insights-" + startDate + "-to-" + endDate + ".pdf\"")
                    .body(insightsExportService.exportPdf(dashboard));
        }
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Choose Excel or PDF export format.");
    }

    private InsightsDashboardService.Dashboard createInsightsDashboard(LocalDate startDate, LocalDate endDate) {
        try {
            return insightsDashboardService.create(startDate, endDate);
        } catch (IllegalArgumentException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/system-logs")
    public String systemLogs(@RequestParam(required = false) String q, Model model) {
        List<CustomerDto> reports = complianceService.fetchAll();
        List<SystemLogEntry> allLogs = reports.stream()
                .filter(Objects::nonNull)
                .flatMap(report -> {
                    List<AuditLogDto> auditLogs = report.getAuditLogs();
                    if (auditLogs == null) {
                        return java.util.stream.Stream.empty();
                    }
                    return auditLogs.stream()
                            .filter(Objects::nonNull)
                            .map(log -> new SystemLogEntry(
                                    log.getActionDate(),
                                    log.getActionedBy(),
                                    log.getActionTaken(),
                                    report.getAmlId(),
                                    report.getFullName(),
                                    report.getCategory(),
                                    report.getStatus()));
                })
                .sorted(Comparator.comparing(SystemLogEntry::actionDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        String search = q == null ? "" : q.trim();
        List<SystemLogEntry> logs = allLogs.stream()
                .filter(log -> matchesSearch(log, search))
                .toList();
        List<ActivitySummary> activities = logs.stream()
                .collect(Collectors.groupingBy(
                        log -> displayValue(log.actionedBy(), "Compliance System"),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    List<SystemLogEntry> userLogs = entry.getValue();
                    SystemLogEntry latest = userLogs.get(0);
                    return new ActivitySummary(entry.getKey(), userLogs.size(), latest.actionDate(),
                            latest.actionTaken(), latest.amlId(), latest.customerName());
                })
                .sorted(Comparator.comparing(ActivitySummary::lastActivity,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        addModuleAttributes(model, "system-logs");
        model.addAttribute("systemLogs", logs);
        model.addAttribute("activityLogs", activities);
        model.addAttribute("systemLogCount", logs.size());
        model.addAttribute("activityLogCount", activities.size());
        model.addAttribute("systemLogSearch", search);
        model.addAttribute("totalProfiles", reports.size());
        return "portal/module";
    }

    private boolean matchesSearch(SystemLogEntry log, String search) {
        if (search.isEmpty()) {
            return true;
        }
        String query = search.toLowerCase(Locale.ROOT);
        return java.util.stream.Stream.of(log.actionedBy(), log.actionTaken(), log.amlId(),
                        log.customerName(), log.category(), log.processStatus())
                .filter(Objects::nonNull)
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(query));
    }

    private String displayValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @GetMapping("/developer-tools")
    public String developerTools(Model model) {
        addModuleAttributes(model, "developer-tools");
        return "portal/module";
    }

    @GetMapping({"/configurations", "/packages", "/help", "/user-guide", "/onboarding/deep-link"})
    public String module(Model model, jakarta.servlet.http.HttpServletRequest request) {
        String path = request.getRequestURI();
        String key = path.substring(path.lastIndexOf('/') + 1);
        if ("deep-link".equals(key)) {
            key = "deep-link";
        }
        addModuleAttributes(model, key);
        List<CustomerDto> reports = complianceService.fetchAll();
        model.addAttribute("totalProfiles", reports.size());
        model.addAttribute("pendingPassports", reports.stream()
                .filter(report -> !"VERIFIED".equals(report.getPassportVerificationStatus())).count());
        model.addAttribute("inReview", reports.stream()
                .filter(report -> "IN_REVIEW".equals(report.getStatus())).count());
        model.addAttribute("approved", reports.stream()
                .filter(report -> "APPROVED".equals(report.getStatus()) || "ACTIVE".equals(report.getStatus())).count());
        return "portal/module";
    }

    private void addModuleAttributes(Model model, String key) {
        ModuleInfo info = MODULES.get(key);
        model.addAttribute("moduleKey", key);
        model.addAttribute("moduleTitle", info.title());
        model.addAttribute("moduleDescription", info.description());
        model.addAttribute("moduleMessage", info.message());
    }

    private record ModuleInfo(String title, String description, String message) {
    }

    private record SystemLogEntry(LocalDate actionDate, String actionedBy, String actionTaken,
                                  String amlId, String customerName, String category, String processStatus) {
    }

    private record ActivitySummary(String actor, long actionCount, LocalDate lastActivity,
                                   String latestAction, String latestAmlId, String latestCustomer) {
    }
}
