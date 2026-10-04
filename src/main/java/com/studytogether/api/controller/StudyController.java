package com.studytogether.api.controller;

import com.studytogether.api.service.StudyService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudyController {
    private final StudyService studyService;

    public StudyController(StudyService studyService) {
        this.studyService = studyService;
    }

    @GetMapping({"/health", "/"})
    public Map<String, String> health() {
        return studyService.health();
    }

    @GetMapping("/workspace")
    public ResponseEntity<?> workspace(HttpServletRequest request) {
        return studyService.workspace(request);
    }

    @GetMapping("/study-plans")
    public ResponseEntity<?> studyPlans(HttpServletRequest request) {
        return studyService.studyPlans(request);
    }

    @GetMapping("/study-stats")
    public Map<String, Object> studyStats(HttpServletRequest request) {
        return studyService.studyStats(request);
    }

    @PostMapping("/study-sessions")
    public ResponseEntity<?> logSession(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return studyService.logSession(body, request);
    }

    @PostMapping("/study-plans/{planId}/topics")
    public ResponseEntity<?> addTopic(@PathVariable String planId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return studyService.addTopic(planId, body, request);
    }

    @PatchMapping("/topics/{topicId}")
    public ResponseEntity<?> updateTopic(@PathVariable String topicId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return studyService.updateTopic(topicId, body, request);
    }

    @DeleteMapping("/topics/{topicId}")
    public ResponseEntity<?> deleteTopic(@PathVariable String topicId, HttpServletRequest request) {
        return studyService.deleteTopic(topicId, request);
    }

    @PostMapping("/workspace/invite/rotate")
    public ResponseEntity<?> rotateInvite(HttpServletRequest request) {
        return studyService.rotateInvite(request);
    }
}
