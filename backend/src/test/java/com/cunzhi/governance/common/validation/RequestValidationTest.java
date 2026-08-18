package com.cunzhi.governance.common.validation;

import com.cunzhi.governance.grid.dto.GridAssignmentsRequest;
import com.cunzhi.governance.resident.dto.ResidentUpdateRequest;
import com.cunzhi.governance.resident.dto.ResidentSensitiveSearchRequest;
import com.cunzhi.governance.resident.dto.ResidentSensitiveViewRequest;
import com.cunzhi.governance.system.dto.UserCreateRequest;
import com.cunzhi.governance.task.dto.TaskActionRequest;
import com.cunzhi.governance.task.dto.TaskCreateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsEventHandleTypeForIndependentTask() {
        TaskCreateRequest request = new TaskCreateRequest(
                "7", "EVENT_HANDLE", "任务", null, "MEDIUM", "12", null
        );

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("taskType"));
    }

    @Test
    void requiresRolesAndExactlyTypedAssignmentFieldsAtDtoBoundary() {
        UserCreateRequest user = new UserCreateRequest(
                "worker", "password123", "网格员", null, Set.of()
        );
        GridAssignmentsRequest assignments = new GridAssignmentsRequest(0, List.of());

        assertThat(validator.validate(user)).isNotEmpty();
        assertThat(validator.validate(assignments)).isNotEmpty();
    }

    @Test
    void requiresVersionForResidentProfileUpdate() {
        ResidentUpdateRequest request = new ResidentUpdateRequest(
                null, "居民", "UNKNOWN", LocalDate.of(1990, 1, 1),
                null, null, "测试地址", false, List.of(), null, null
        );

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("version"));
    }

    @Test
    void rejectsNonStringCompatibleAttachmentIdentifiers() {
        TaskActionRequest request = new TaskActionRequest(
                0, null, null, "已完成", List.of("12", "bad-id"), null, null
        );

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .contains("attachmentIds"));
    }

    @Test
    void validatesSensitiveSearchBoundaryAndViewPurpose() {
        ResidentSensitiveSearchRequest search = new ResidentSensitiveSearchRequest(
                "UNKNOWN", "13800000000", null, null, 0, 101
        );
        ResidentSensitiveViewRequest view = new ResidentSensitiveViewRequest("查看");

        assertThat(validator.validate(search)).hasSize(3);
        assertThat(validator.validate(view))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("purpose"));
    }
}
