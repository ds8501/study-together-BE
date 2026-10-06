package com.studytogether.api.controller;

import com.studytogether.api.model.dto.request.CreateTopicRequest;
import com.studytogether.api.model.dto.request.LogSessionRequest;
import com.studytogether.api.model.dto.request.UpdateTopicRequest;
import com.studytogether.api.model.dto.response.*;
import com.studytogether.api.service.StudyService;
import jakarta.servlet.http.HttpServletRequest;
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
    public HealthResponse health() {
        return studyService.health();
    }

    @GetMapping("/workspace")
    public ResponseEntity<WorkspaceResponse> workspace(HttpServletRequest request) {
        return studyService.workspace(request);
    }

    @GetMapping("/study-plans")
    public ResponseEntity<StudyPlansResponse> studyPlans(HttpServletRequest request) {
        return studyService.studyPlans(request);
    }

    @GetMapping("/study-stats")
    public StudyStatsResponse studyStats(HttpServletRequest request) {
        return studyService.studyStats(request);
    }

    @PostMapping("/study-sessions")
    public ResponseEntity<LogSessionResponse> logSession(@RequestBody LogSessionRequest request, HttpServletRequest httpRequest) {
        return studyService.logSession(request, httpRequest);
    }

    @PostMapping("/study-plans/{planId}/topics")
    public ResponseEntity<TopicSingleResponse> addTopic(@PathVariable Long planId, @RequestBody CreateTopicRequest request, HttpServletRequest httpRequest) {
        return studyService.addTopic(planId, request, httpRequest);
    }

    @PatchMapping("/topics/{topicId}")
    public ResponseEntity<TopicSingleResponse> updateTopic(@PathVariable Long topicId, @RequestBody UpdateTopicRequest request, HttpServletRequest httpRequest) {
        return studyService.updateTopic(topicId, request, httpRequest);
    }

    @DeleteMapping("/topics/{topicId}")
    public ResponseEntity<Void> deleteTopic(@PathVariable Long topicId, HttpServletRequest httpRequest) {
        return studyService.deleteTopic(topicId, httpRequest);
    }

    @PostMapping("/workspace/invite/rotate")
    public ResponseEntity<RotateInviteResponse> rotateInvite(HttpServletRequest httpRequest) {
        return studyService.rotateInvite(httpRequest);
    }
}
