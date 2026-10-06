package com.studytogether.api.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkspaceResponse(
        Long id,
        String name,
        String inviteCode,
        List<WorkspaceMemberResponse> members
) {}
