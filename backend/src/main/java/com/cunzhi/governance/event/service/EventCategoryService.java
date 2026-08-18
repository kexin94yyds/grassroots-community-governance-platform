package com.cunzhi.governance.event.service;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.common.id.IdParser;
import com.cunzhi.governance.event.dto.EventCategoryCreateRequest;
import com.cunzhi.governance.event.dto.EventCategoryUpdateRequest;
import com.cunzhi.governance.event.dto.EventCategoryView;
import com.cunzhi.governance.event.mapper.EventCategoryMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EventCategoryService {

    private final EventCategoryMapper mapper;

    public EventCategoryService(EventCategoryMapper mapper) {
        this.mapper = mapper;
    }

    public List<EventCategoryView> findAll() {
        return mapper.findAll().stream().map(this::toView).toList();
    }

    @Transactional
    public EventCategoryView create(EventCategoryCreateRequest request) {
        String code = request.code().trim();
        if (mapper.countByCode(code) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "事件类别编码已存在");
        }
        EventCategoryMapper.CategoryRow row = new EventCategoryMapper.CategoryRow(
                null, code, request.name().trim(), normalize(request.description()),
                request.sortNo() == null ? 0 : request.sortNo(),
                request.status() == null ? "ENABLED" : request.status(), 0
        );
        try {
            if (mapper.insert(row) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "事件类别保存失败");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "事件类别编码已存在");
        }
        Long id = mapper.findIdByCode(code);
        if (id == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "事件类别保存后未能读取记录");
        }
        return toView(require(id));
    }

    @Transactional
    public EventCategoryView update(String idValue, EventCategoryUpdateRequest request) {
        long id = IdParser.parse(idValue, "事件类别ID");
        EventCategoryMapper.CategoryRow current = requireForUpdate(id);
        if ("ENABLED".equals(current.status()) && "DISABLED".equals(request.status())
                && mapper.countNonTerminalEvents(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "类别仍被未终结事件使用，不能停用");
        }
        EventCategoryMapper.CategoryRow updated = new EventCategoryMapper.CategoryRow(
                id, current.code(), request.name().trim(), normalize(request.description()),
                request.sortNo(), request.status(), request.version()
        );
        if (mapper.update(updated) != 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
        return toView(require(id));
    }

    private EventCategoryMapper.CategoryRow require(long id) {
        EventCategoryMapper.CategoryRow row = mapper.findById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "事件类别不存在");
        }
        return row;
    }

    private EventCategoryMapper.CategoryRow requireForUpdate(long id) {
        EventCategoryMapper.CategoryRow row = mapper.findByIdForUpdate(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "事件类别不存在");
        }
        return row;
    }

    private EventCategoryView toView(EventCategoryMapper.CategoryRow row) {
        return new EventCategoryView(row.id().toString(), row.code(), row.name(), row.description(),
                row.sortNo(), row.status(), row.version());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
