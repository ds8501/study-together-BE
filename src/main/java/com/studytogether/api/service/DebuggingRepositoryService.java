package com.studytogether.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Service
public class DebuggingRepositoryService {
    private static final List<String> IDS = List.of(
            "inventory-reservation-java", "task-scheduler-java", "wallet-ledger-java");
    private static final Map<String, Guidance> GUIDANCE = Map.of(
            "inventory-reservation-java", new Guidance(
                    List.of("Check what happens when the same reservation ID is submitted twice.", "The release flag exists in the reservation record. Make release idempotent and validate quantities before changing stock."),
                    "Maintain inventory invariants by validating positive quantities and known SKUs before mutation, refusing duplicate IDs, and returning false when an already released reservation is released again."),
            "task-scheduler-java", new Guidance(
                    List.of("A task already in RUNNING state is currently eligible for another claim.", "Treat every state transition as an atomic operation; synchronized methods are a straightforward fit for this in-memory example."),
                    "Claim only due PENDING tasks and change status while holding the scheduler lock. Reject duplicate IDs and invalid transitions so concurrent workers cannot claim the same task and completed or cancelled tasks cannot move backwards."),
            "wallet-ledger-java", new Guidance(
                    List.of("Compare the withdrawal condition with the exact-balance test in LedgerTest.", "A set of processed IDs cannot tell whether a retry has the same payload. Validate the full transaction before applying changes."),
                    "Amounts must be positive, overdrafts must be rejected before debit, and exact-balance withdrawals are valid. Store transaction details as idempotency records so a matching replay is accepted while reuse with a different operation or payload is rejected."));

    private final ObjectMapper mapper;
    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public DebuggingRepositoryService(ObjectMapper mapper) { this.mapper = mapper; }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> repositories = new ArrayList<>();
        for (String id : IDS) repositories.add(get(id));
        return repositories;
    }

    public Map<String, Object> get(String id) {
        if (!IDS.contains(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found");
        try {
            Resource metadataResource = resolver.getResource("classpath:debugging-repositories/" + id + "/challenge.json");
            Map<String, Object> result = mapper.readValue(metadataResource.getInputStream(), new TypeReference<>() {});
            Map<String, String> files = new LinkedHashMap<>();
            for (Resource resource : resolver.getResources("classpath*:debugging-repositories/" + id + "/src/**/*.java")) {
                String url = resource.getURL().toString();
                String marker = "/" + id + "/src/";
                int offset = url.indexOf(marker);
                if (offset >= 0) files.put("src/" + url.substring(offset + marker.length()), new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            }
            result.put("id", id);
            result.put("name", id);
            result.put("summary", result.get("candidateBrief"));
            result.put("accessLevel", "FREE");
            result.put("files", files);
            result.put("hints", GUIDANCE.get(id).hints());
            result.put("explanation", GUIDANCE.get(id).explanation());
            result.put("checks", checkNames(id));
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Could not load challenge repository " + id, e);
        }
    }

    public Map<String, Object> evaluate(String id, Map<String, String> files) {
        Map<String, Object> repository = get(id);
        List<Map<String, Object>> outcomes = new ArrayList<>();
        for (Rule rule : rules(id)) {
            boolean passed = rule.test().test(files.getOrDefault(rule.file(), ""));
            outcomes.add(Map.of("name", rule.name(), "passed", passed));
        }
        boolean allPassed = outcomes.stream().allMatch(outcome -> Boolean.TRUE.equals(outcome.get("passed")));
        return Map.of("repositoryId", repository.get("id"), "checks", outcomes, "allPassed", allPassed);
    }

    private List<String> checkNames(String id) { return rules(id).stream().map(Rule::name).toList(); }
    private static Rule rule(String name, String file, String regex) { return new Rule(name, file, source -> Pattern.compile(regex, Pattern.DOTALL).matcher(source).find()); }
    private static Rule methodRule(String name, String file, String method, String regex) {
        return new Rule(name, file, source -> {
            int start = source.indexOf(method);
            if (start < 0) return false;
            int end = source.indexOf("\n    public ", start + method.length());
            String body = end < 0 ? source.substring(start) : source.substring(start, end);
            return Pattern.compile(regex, Pattern.DOTALL).matcher(body).find();
        });
    }
    private static final String INVENTORY = "src/main/java/interview/Inventory.java";
    private static final String SCHEDULER = "src/main/java/interview/TaskScheduler.java";
    private static final String LEDGER = "src/main/java/interview/WalletLedger.java";

    private List<Rule> rules(String id) {
        return switch (id) {
            case "inventory-reservation-java" -> List.of(
                    methodRule("Stock additions reject nonpositive quantities", INVENTORY, "public void addStock", "quantity\\s*<=\\s*0"),
                    methodRule("Reservations reject nonpositive quantities", INVENTORY, "public boolean reserve", "quantity\\s*<=\\s*0"),
                    methodRule("Unknown SKUs cannot be reserved", INVENTORY, "public boolean reserve", "stock\\.containsKey\\s*\\(\\s*sku\\s*\\)"),
                    methodRule("Reservation identifiers cannot overwrite existing ones", INVENTORY, "public boolean reserve", "reservations\\.containsKey\\s*\\(\\s*reservationId\\s*\\)"),
                    new Rule("A reservation can only be released once", INVENTORY, source -> Pattern.compile("if\\s*\\(\\s*reservation\\.released\\s*\\)").matcher(source).find() && Pattern.compile("reservation\\.released\\s*=\\s*true").matcher(source).find()));
            case "task-scheduler-java" -> List.of(
                    rule("Duplicate task IDs are rejected", SCHEDULER, "tasks\\.containsKey\\s*\\(\\s*task\\.getId\\(\\)\\s*\\)"),
                    rule("Only pending tasks can be claimed", SCHEDULER, "Status\\.PENDING[\\s\\S]*claimDueTasks"),
                    rule("Task claims and state changes are synchronized", SCHEDULER, "synchronized"),
                    rule("Only running tasks can be completed", SCHEDULER, "Status\\.RUNNING[\\s\\S]*complete\\s*\\("),
                    rule("Only pending tasks can be cancelled", SCHEDULER, "Status\\.PENDING[\\s\\S]*cancel\\s*\\("));
            case "wallet-ledger-java" -> List.of(
                    rule("Deposits reject nonpositive amounts", LEDGER, "amount\\.signum\\(\\)\\s*<=\\s*0"),
                    rule("Withdrawals allow exact-balance withdrawals", LEDGER, "compareTo\\(amount\\)\\s*<\\s*0"),
                    rule("Failed or overdraft transfers do not change balances", LEDGER, "from\\.getBalance\\(\\)\\.compareTo\\(amount\\)\\s*<\\s*0"),
                    rule("Transaction replay checks the original operation and payload", LEDGER, "processedTransactions|transactionRecords|Map\\s*<\\s*String\\s*,\\s*Transaction"),
                    new Rule("Transfers validate before mutating either wallet", LEDGER, source -> {
                        int validation = source.indexOf("compareTo(amount) < 0");
                        int debit = source.indexOf(".debit(");
                        return validation >= 0 && debit > validation;
                    }));
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found");
        };
    }

    private record Guidance(List<String> hints, String explanation) {}
    private record Rule(String name, String file, Predicate<String> test) {}
}
