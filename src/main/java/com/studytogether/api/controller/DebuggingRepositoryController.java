package com.studytogether.api.controller;

import com.studytogether.api.service.DebuggingRepositoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/debugging-repositories")
public class DebuggingRepositoryController {
    private final DebuggingRepositoryService repositories;
    public DebuggingRepositoryController(DebuggingRepositoryService repositories) { this.repositories = repositories; }

    @GetMapping
    public List<Map<String, Object>> list() { return repositories.list(); }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable("id") String id) { return repositories.get(id); }

    @PostMapping("/{id}/evaluate")
    public Map<String, Object> evaluate(@PathVariable("id") String id, @RequestBody Map<String, Map<String, String>> request) {
        return repositories.evaluate(id, request.getOrDefault("files", Map.of()));
    }
}
