package com.cunzhi.governance.event.service;

import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.api.PageResponse;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.event.domain.EventStatus;
import com.cunzhi.governance.event.dto.EventActionRequest;
import com.cunzhi.governance.event.dto.EventCategoryOption;
import com.cunzhi.governance.event.dto.EventCreateRequest;
import com.cunzhi.governance.event.dto.EventDispatchRequest;
import com.cunzhi.governance.event.dto.EventFlowView;
import com.cunzhi.governance.event.dto.EventSummary;
import com.cunzhi.governance.event.mapper.EventFlowMapper;
import com.cunzhi.governance.event.mapper.EventMapper;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.service.DataScopeService;
import com.cunzhi.governance.task.mapper.TaskFlowMapper;
import com.cunzhi.governance.task.mapper.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class EventService {

    private final EventMapper eventMapper;
    private final EventFlowMapper eventFlowMapper;
    private final TaskMapper taskMapper;
    private final TaskFlowMapper taskFlowMapper;
    private final DataScopeMapper dataScopeMapper;
    private final DataScopeService dataScopeService;
    private final BusinessNumberGenerator numberGenerator;

    public EventService(
            EventMapper eventMapper,
            EventFlowMapper eventFlowMapper,
            TaskMapper taskMapper,
            TaskFlowMapper taskFlowMapper,
            DataScopeMapper dataScopeMapper,
            DataScopeService dataScopeService,
            BusinessNumberGenerator numberGenerator
    ) {
        this.eventMapper = eventMapper;
        this.eventFlowMapper = eventFlowMapper;
        this.taskMapper = taskMapper;
        this.taskFlowMapper = taskFlowMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.dataScopeService = dataScopeService;
        this.numberGenerator = numberGenerator;
    }

    public EventSummary findById(String id) {
        EventMapper.EventRow row = requireEvent(IdParser.parse(id, "事件ID"));
        dataScopeService.requireGridAccess(row.gridId());
        return toSummary(row);
    }

    public PageResponse<EventSummary> findPage(String keyword, String status, int page, int size) {
        DataScope scope = dataScopeService.currentScope();
        boolean allAccess = scope.type() == DataScopeType.ALL;
        List<Long> gridIds = new ArrayList<>(scope.gridIds());
        String normalizedKeyword = normalizeText(keyword);
        String normalizedStatus = normalizeStatus(status);

        List<EventSummary> items = eventMapper.findPage(
                        normalizedKeyword, normalizedStatus, allAccess, gridIds,
                        (page - 1) * size, size
                ).stream()
                .map(this::toSummary)
                .toList();
        long total = eventMapper.count(normalizedKeyword, normalizedStatus, allAccess, gridIds);
        return new PageResponse<>(items, total, page, size);
    }

    public List<EventFlowView> findFlows(String id) {
        long eventId = IdParser.parse(id, "事件ID");
        EventMapper.EventRow event = requireEvent(eventId);
        dataScopeService.requireGridAccess(event.gridId());
        return eventFlowMapper.findByEventId(eventId).stream()
                .map(row -> new EventFlowView(
                        row.id().toString(),
                        row.eventId().toString(),
                        row.taskId() == null ? null : row.taskId().toString(),
                        row.action(),
                        row.fromStatus(),
                        row.toStatus(),
                        row.operatorUserId() == null ? null : row.operatorUserId().toString(),
                        row.operatorName(),
                        row.remark(),
                        row.createdAt()
                ))
                .toList();
    }

    public List<EventCategoryOption> findEnabledCategories() {
        return eventMapper.findEnabledCategories().stream()
                .map(row -> new EventCategoryOption(row.id().toString(), row.code(), row.name()))
                .toList();
    }

    public List<EventSummary> findReportedByCurrentUser() {
        return eventMapper.findByReporterUserId(dataScopeService.currentUser().id()).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public EventSummary report(EventCreateRequest request) {
        long gridId = IdParser.parse(request.gridId(), "网格ID");
        long categoryId = IdParser.parse(request.categoryId(), "类别ID");
        if (dataScopeMapper.countEnabledGrid(gridId) != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "事件必须归属有效网格");
        }
        dataScopeService.requireGridAccess(gridId);
        if (eventMapper.lockEnabledCategory(categoryId) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "事件类别不存在或已停用");
        }
        AuthenticatedUser operator = dataScopeService.currentUser();
        String eventNo = numberGenerator.next("EVT");
        eventMapper.insert(
                eventNo, categoryId, gridId, request.title(), request.description(),
                request.reportChannel(), request.severity(), request.address(),
                operator.id(), request.reporterName()
        );
        long eventId = eventMapper.findIdByEventNo(eventNo);
        eventFlowMapper.insert(eventId, null, "REPORT", null, EventStatus.REPORTED.name(),
                operator.id(), "事件上报");
        return toSummary(requireEvent(eventId));
    }

    @Transactional
    public EventSummary accept(String id, EventActionRequest request) {
        return transition(id, request, EventStatus.ACCEPTED, "ACCEPT");
    }

    @Transactional
    public EventSummary reject(String id, EventActionRequest request) {
        return transition(id, request, EventStatus.REJECTED, "REJECT");
    }

    @Transactional
    public EventSummary cancel(String id, EventActionRequest request) {
        return transition(id, request, EventStatus.CANCELLED, "CANCEL");
    }

    @Transactional
    public EventSummary assign(String id, EventDispatchRequest request) {
        long eventId = IdParser.parse(id, "事件ID");
        long assigneeId = IdParser.parse(request.assigneeUserId(), "执行人ID");
        EventMapper.EventRow row = requireEvent(eventId);
        dataScopeService.requireGridAccess(row.gridId());
        if (dataScopeMapper.lockEnabledGrid(row.gridId()) == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "事件所属网格已停用");
        }
        EventStatus from = EventStatus.valueOf(row.status());
        from.requireTransitionTo(EventStatus.ASSIGNED);
        if (dataScopeMapper.countActiveGridWorkerAssignment(assigneeId, row.gridId()) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "执行人不是该网格的有效网格员");
        }

        ensureUpdated(eventMapper.transition(
                eventId, from.name(), EventStatus.ASSIGNED.name(), request.version(),
                assigneeId, null
        ));
        if (taskMapper.countActiveByEventId(eventId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该事件已存在未终止任务");
        }

        AuthenticatedUser operator = dataScopeService.currentUser();
        String taskNo = numberGenerator.next("TSK");
        taskMapper.insertEventTask(
                taskNo, eventId, row.gridId(),
                request.taskTitle() == null || request.taskTitle().isBlank() ? row.title() : request.taskTitle(),
                request.taskDescription() == null ? row.description() : request.taskDescription(),
                request.priority(), operator.id(), assigneeId, request.dueAt()
        );
        long taskId = taskMapper.findIdByTaskNo(taskNo);
        taskFlowMapper.insert(taskId, "ASSIGN", null, "PENDING_ACCEPT", operator.id(), request.remark());
        eventFlowMapper.insert(
                eventId, taskId, "ASSIGN", from.name(), EventStatus.ASSIGNED.name(),
                operator.id(), request.remark()
        );
        return toSummary(requireEvent(eventId));
    }

    private EventSummary transition(
            String id,
            EventActionRequest request,
            EventStatus target,
            String action
    ) {
        long eventId = IdParser.parse(id, "事件ID");
        EventMapper.EventRow row = requireEvent(eventId);
        dataScopeService.requireGridAccess(row.gridId());
        EventStatus from = EventStatus.valueOf(row.status());
        from.requireTransitionTo(target);
        String flowRemark = request.remark();
        if (target == EventStatus.REJECTED || target == EventStatus.CANCELLED) {
            flowRemark = normalizeText(request.reason());
            if (flowRemark == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "驳回或撤销必须填写原因");
            }
        }
        ensureUpdated(eventMapper.transition(eventId, from.name(), target.name(), request.version(), null, null));
        eventFlowMapper.insert(
                eventId, null, action, from.name(), target.name(),
                dataScopeService.currentUser().id(), flowRemark
        );
        return toSummary(requireEvent(eventId));
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeText(status);
        if (normalized == null) {
            return null;
        }
        try {
            return EventStatus.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知事件状态");
        }
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private EventMapper.EventRow requireEvent(long id) {
        return eventMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "事件不存在"));
    }

    private void ensureUpdated(int updated) {
        if (updated != 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private EventSummary toSummary(EventMapper.EventRow row) {
        return new EventSummary(
                row.id().toString(), row.eventNo(), row.categoryId().toString(), row.categoryName(),
                row.gridId().toString(), row.gridName(),
                row.title(), row.description(), row.address(),
                row.reportChannel(), row.severity(), row.status(),
                row.assignedToUserId() == null ? null : row.assignedToUserId().toString(),
                row.assignedToName(),
                row.resultSummary(), row.reportedAt(), row.version()
        );
    }
}
