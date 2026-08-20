package com.cunzhi.governance.resident.service;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.event.dto.EventCreateRequest;
import com.cunzhi.governance.event.dto.EventSummary;
import com.cunzhi.governance.event.service.EventService;
import com.cunzhi.governance.resident.dto.ResidentEventRequest;
import com.cunzhi.governance.resident.dto.ResidentPortalOverview;
import com.cunzhi.governance.resident.dto.ResidentPortalProfileUpdateRequest;
import com.cunzhi.governance.resident.dto.ResidentSummary;
import com.cunzhi.governance.system.security.RoleCodes;
import com.cunzhi.governance.system.service.DataScopeService;
import org.springframework.stereotype.Service;

@Service
public class ResidentPortalService {

    private final ResidentService residentService;
    private final EventService eventService;
    private final DataScopeService dataScopeService;

    public ResidentPortalService(
            ResidentService residentService,
            EventService eventService,
            DataScopeService dataScopeService
    ) {
        this.residentService = residentService;
        this.eventService = eventService;
        this.dataScopeService = dataScopeService;
    }

    public ResidentPortalOverview overview() {
        requireResidentRole();
        return new ResidentPortalOverview(
                residentService.findByCurrentUser(),
                eventService.findEnabledCategories(),
                eventService.findReportedByCurrentUser()
        );
    }

    public EventSummary report(ResidentEventRequest request) {
        requireResidentRole();
        ResidentSummary profile = residentService.findByCurrentUser();
        return eventService.report(new EventCreateRequest(
                request.categoryId(),
                profile.gridId(),
                request.title(),
                request.description(),
                "WEB",
                request.severity(),
                request.address() == null || request.address().isBlank()
                        ? profile.address()
                        : request.address().trim(),
                profile.realName()
        ));
    }

    public ResidentSummary updateProfile(ResidentPortalProfileUpdateRequest request) {
        requireResidentRole();
        return residentService.updateCurrentUserProfile(request);
    }

    private void requireResidentRole() {
        if (!dataScopeService.currentUser().roles().contains(RoleCodes.RESIDENT)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅居民账号可访问居民服务台");
        }
    }
}
