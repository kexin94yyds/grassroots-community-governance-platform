package com.cunzhi.governance.announcement.service;

import com.cunzhi.governance.announcement.domain.AnnouncementStatus;
import com.cunzhi.governance.announcement.dto.AnnouncementActionRequest;
import com.cunzhi.governance.announcement.dto.AnnouncementCreateRequest;
import com.cunzhi.governance.announcement.dto.AnnouncementFlowView;
import com.cunzhi.governance.announcement.dto.AnnouncementUpdateRequest;
import com.cunzhi.governance.announcement.dto.AnnouncementView;
import com.cunzhi.governance.announcement.mapper.AnnouncementFlowMapper;
import com.cunzhi.governance.announcement.mapper.AnnouncementMapper;
import com.cunzhi.governance.auth.model.AuthenticatedUser;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.BusinessNumberGenerator;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.system.mapper.DataScopeMapper;
import com.cunzhi.governance.system.security.DataScope;
import com.cunzhi.governance.system.security.DataScopeType;
import com.cunzhi.governance.system.security.PermissionCodes;
import com.cunzhi.governance.system.security.RoleCodes;
import com.cunzhi.governance.system.service.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;
    private final AnnouncementFlowMapper flowMapper;
    private final DataScopeMapper dataScopeMapper;
    private final DataScopeService dataScopeService;
    private final BusinessNumberGenerator numberGenerator;

    public AnnouncementService(
            AnnouncementMapper announcementMapper,
            AnnouncementFlowMapper flowMapper,
            DataScopeMapper dataScopeMapper,
            DataScopeService dataScopeService,
            BusinessNumberGenerator numberGenerator
    ) {
        this.announcementMapper = announcementMapper;
        this.flowMapper = flowMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.dataScopeService = dataScopeService;
        this.numberGenerator = numberGenerator;
    }

    public List<AnnouncementView> findVisible() {
        AuthenticatedUser user = dataScopeService.currentUser();
        DataScope scope = dataScopeService.currentScope();
        boolean manager = user.permissions().contains(PermissionCodes.ANNOUNCEMENT_GLOBAL_WRITE)
                || user.permissions().contains(PermissionCodes.ANNOUNCEMENT_COMMUNITY_WRITE);
        return announcementMapper.findVisible(
                        scope.type() == DataScopeType.ALL,
                        new ArrayList<>(scope.gridIds()),
                        manager
                ).stream()
                .map(this::toView)
                .toList();
    }

    public List<AnnouncementFlowView> findFlows(String idValue) {
        AnnouncementMapper.AnnouncementRow row = requireReadable(IdParser.parse(idValue, "公告ID"));
        return flowMapper.findByAnnouncementId(row.id()).stream()
                .map(item -> new AnnouncementFlowView(
                        item.id().toString(), item.action(), item.fromStatus(), item.toStatus(),
                        item.operatorUserId().toString(), item.operatorName(), item.remark(), item.createdAt()
                )).toList();
    }

    public AnnouncementView findById(String idValue) {
        return toView(requireReadable(IdParser.parse(idValue, "公告ID")));
    }

    @Transactional
    public AnnouncementView create(AnnouncementCreateRequest request) {
        AuthenticatedUser operator = dataScopeService.currentUser();
        String audienceScope = request.audienceScope().trim();
        Long communityId = null;
        if ("GLOBAL".equals(audienceScope)) {
            requireGlobalWriter(operator);
        } else {
            communityId = resolveCommunityId(request.communityId(), operator);
            requireCommunityWriter(operator, communityId);
        }
        String announcementNo = numberGenerator.next("ANN");
        ensureUpdated(announcementMapper.insert(
                announcementNo, audienceScope, communityId, request.title().trim(), request.content().trim(),
                request.pinned(), operator.id()
        ));
        Long id = announcementMapper.findIdByAnnouncementNo(announcementNo);
        if (id == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建公告后未能读取公告");
        }
        ensureUpdated(flowMapper.insert(id, "CREATE", null, AnnouncementStatus.DRAFT.name(), operator.id(), "创建公告"));
        return toView(requireAnnouncement(id));
    }

    @Transactional
    public AnnouncementView update(String idValue, AnnouncementUpdateRequest request) {
        long id = IdParser.parse(idValue, "公告ID");
        AnnouncementMapper.AnnouncementLockRow current = requireLocked(id);
        requireWriteScope(current);
        if (AnnouncementStatus.DRAFT != AnnouncementStatus.valueOf(current.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "仅草稿公告可编辑");
        }
        ensureUpdated(announcementMapper.updateDraft(
                id, request.title().trim(), request.content().trim(), request.pinned(), request.version()
        ));
        ensureUpdated(flowMapper.insert(id, "UPDATE", current.status(), current.status(),
                dataScopeService.currentUser().id(), "更新草稿公告"));
        return toView(requireAnnouncement(id));
    }

    @Transactional
    public AnnouncementView publish(String idValue, AnnouncementActionRequest request) {
        return transition(idValue, request, AnnouncementStatus.PUBLISHED, "PUBLISH");
    }

    @Transactional
    public AnnouncementView withdraw(String idValue, AnnouncementActionRequest request) {
        return transition(idValue, request, AnnouncementStatus.WITHDRAWN, "WITHDRAW");
    }

    private AnnouncementView transition(
            String idValue,
            AnnouncementActionRequest request,
            AnnouncementStatus target,
            String action
    ) {
        long id = IdParser.parse(idValue, "公告ID");
        AnnouncementMapper.AnnouncementLockRow current = requireLocked(id);
        requireWriteScope(current);
        AnnouncementStatus from = AnnouncementStatus.valueOf(current.status());
        from.requireTransitionTo(target);
        String remark = normalizeText(request.remark());
        if (remark == null) {
            remark = normalizeText(request.reason());
        }
        if (target == AnnouncementStatus.WITHDRAWN && remark == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "撤回公告必须填写原因");
        }
        AuthenticatedUser operator = dataScopeService.currentUser();
        ensureUpdated(announcementMapper.transition(id, from.name(), target.name(), operator.id(), request.version()));
        ensureUpdated(flowMapper.insert(id, action, from.name(), target.name(), operator.id(), remark));
        return toView(requireAnnouncement(id));
    }

    private AnnouncementMapper.AnnouncementRow requireReadable(long id) {
        AnnouncementMapper.AnnouncementRow row = requireAnnouncement(id);
        AuthenticatedUser user = dataScopeService.currentUser();
        boolean manager = user.permissions().contains(PermissionCodes.ANNOUNCEMENT_GLOBAL_WRITE)
                || user.permissions().contains(PermissionCodes.ANNOUNCEMENT_COMMUNITY_WRITE);
        if (!AnnouncementStatus.PUBLISHED.name().equals(row.status())) {
            if (!manager) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
            }
            if ("GLOBAL".equals(row.audienceScope())) {
                requireGlobalWriter(user);
            } else {
                requireCommunityWriter(user, row.communityId());
            }
        }
        if ("GLOBAL".equals(row.audienceScope())) {
            return row;
        }
        DataScope scope = dataScopeService.currentScope();
        if (scope.type() == DataScopeType.ALL
                || (!scope.gridIds().isEmpty()
                && dataScopeMapper.countCommunityInGridScope(row.communityId(), new ArrayList<>(scope.gridIds())) > 0)) {
            return row;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该公告");
    }

    private AnnouncementMapper.AnnouncementRow requireAnnouncement(long id) {
        return announcementMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "公告不存在"));
    }

    private AnnouncementMapper.AnnouncementLockRow requireLocked(long id) {
        AnnouncementMapper.AnnouncementLockRow row = announcementMapper.findLockById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        return row;
    }

    private void requireWriteScope(AnnouncementMapper.AnnouncementLockRow row) {
        AuthenticatedUser operator = dataScopeService.currentUser();
        if ("GLOBAL".equals(row.audienceScope())) {
            requireGlobalWriter(operator);
        } else {
            requireCommunityWriter(operator, row.communityId());
        }
    }

    private void requireGlobalWriter(AuthenticatedUser user) {
        if (!user.roles().contains(RoleCodes.SYSTEM_ADMIN)
                || !user.permissions().contains(PermissionCodes.ANNOUNCEMENT_GLOBAL_WRITE)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅系统管理员可维护全局公告");
        }
    }

    private void requireCommunityWriter(AuthenticatedUser user, Long communityId) {
        if (communityId == null || dataScopeMapper.countEnabledCommunity(communityId) != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权维护该社区公告");
        }
        if (user.roles().contains(RoleCodes.SYSTEM_ADMIN)
                && user.permissions().contains(PermissionCodes.ANNOUNCEMENT_GLOBAL_WRITE)) {
            return;
        }
        if (!user.roles().contains(RoleCodes.COMMUNITY_STAFF)
                || !user.permissions().contains(PermissionCodes.ANNOUNCEMENT_COMMUNITY_WRITE)
                || dataScopeMapper.countActiveCommunityStaffAccess(user.id(), communityId) != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权维护该社区公告");
        }
    }

    private long resolveCommunityId(String communityIdValue, AuthenticatedUser user) {
        if (communityIdValue != null && !communityIdValue.isBlank()) {
            return IdParser.parse(communityIdValue, "社区ID");
        }
        List<Long> communityIds = dataScopeMapper.findActiveCommunityIds(user.id());
        if (communityIds.size() != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请明确选择目标社区");
        }
        return communityIds.get(0);
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureUpdated(int updated) {
        if (updated != 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private AnnouncementView toView(AnnouncementMapper.AnnouncementRow row) {
        return new AnnouncementView(
                row.id().toString(), row.announcementNo(), row.audienceScope(),
                row.communityId() == null ? null : row.communityId().toString(), row.communityName(),
                row.title(), row.content(), row.pinned(), row.status(), row.createdBy().toString(),
                row.createdByName(), row.publishedAt(), row.withdrawnAt(), row.createdAt(), row.version()
        );
    }
}
