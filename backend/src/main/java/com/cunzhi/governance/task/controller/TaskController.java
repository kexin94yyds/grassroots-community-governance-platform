package com.cunzhi.governance.task.controller;

import com.cunzhi.governance.common.api.ApiResponse;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.task.dto.TaskActionRequest;
import com.cunzhi.governance.task.dto.TaskCreateRequest;
import com.cunzhi.governance.task.dto.TaskFlowView;
import com.cunzhi.governance.task.dto.TaskSummary;
import com.cunzhi.governance.task.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @PreAuthorize("@authz.hasPermission('task:read')")
    public ApiResponse<PageResponse<TaskSummary>> findPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(taskService.findPage(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.hasPermission('task:read')")
    public ApiResponse<TaskSummary> findById(@PathVariable String id) {
        return ApiResponse.ok(taskService.findById(id));
    }

    @GetMapping("/{id}/flows")
    @PreAuthorize("@authz.hasPermission('task:read')")
    public ApiResponse<List<TaskFlowView>> findFlows(@PathVariable String id) {
        return ApiResponse.ok(taskService.findFlows(id));
    }

    @PostMapping
    @PreAuthorize("@authz.hasPermission('task:create')")
    public ApiResponse<TaskSummary> create(@Valid @RequestBody TaskCreateRequest request) {
        return ApiResponse.ok(taskService.create(request));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("@authz.hasPermission('task:accept')")
    public ApiResponse<TaskSummary> accept(
            @PathVariable String id,
            @Valid @RequestBody TaskActionRequest request
    ) {
        return ApiResponse.ok(taskService.accept(id, request));
    }

    @PostMapping("/{id}/submit-review")
    @PreAuthorize("@authz.hasPermission('task:handle')")
    public ApiResponse<TaskSummary> submitReview(
            @PathVariable String id,
            @Valid @RequestBody TaskActionRequest request
    ) {
        return ApiResponse.ok(taskService.submitReview(id, request));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("@authz.hasPermission('task:review')")
    public ApiResponse<TaskSummary> review(
            @PathVariable String id,
            @Valid @RequestBody TaskActionRequest request
    ) {
        return ApiResponse.ok(taskService.review(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@authz.hasPermission('task:cancel')")
    public ApiResponse<TaskSummary> cancel(
            @PathVariable String id,
            @Valid @RequestBody TaskActionRequest request
    ) {
        return ApiResponse.ok(taskService.cancel(id, request));
    }
}
