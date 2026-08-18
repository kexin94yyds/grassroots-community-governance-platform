package com.cunzhi.governance.auth.service;

import com.cunzhi.governance.auth.dto.RegistrationRequest;
import com.cunzhi.governance.auth.dto.RegistrationResponse;
import com.cunzhi.governance.auth.mapper.RegistrationMapper;
import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.resident.service.ResidentIdentityNormalizer;
import com.cunzhi.governance.resident.service.SensitiveDataCodec;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RegistrationService {

    private final RegistrationMapper registrationMapper;
    private final PasswordEncoder passwordEncoder;
    private final SensitiveDataCodec sensitiveDataCodec;

    public RegistrationService(
            RegistrationMapper registrationMapper,
            PasswordEncoder passwordEncoder,
            SensitiveDataCodec sensitiveDataCodec
    ) {
        this.registrationMapper = registrationMapper;
        this.passwordEncoder = passwordEncoder;
        this.sensitiveDataCodec = sensitiveDataCodec;
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        String username = request.username().trim();
        if (registrationMapper.countByUsername(username) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }

        String accountType = request.accountType().trim().toUpperCase(Locale.ROOT);
        String realName = request.realName().trim();
        String phone = ResidentIdentityNormalizer.normalizePhone(request.phone());
        Long requestedResidentId = null;

        if ("RESIDENT".equals(accountType)) {
            String idCard = ResidentIdentityNormalizer.normalizeIdCard(request.idCardNumber());
            requestedResidentId = registrationMapper.findAvailableResidentId(
                    realName,
                    sensitiveDataCodec.fingerprint(idCard),
                    sensitiveDataCodec.fingerprint(phone)
            );
            if (requestedResidentId == null || requestedResidentId <= 0
                    || registrationMapper.countRequestsForResident(requestedResidentId) > 0) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "居民身份信息未匹配到可绑定档案，请联系社区工作人员核验"
                );
            }
        }

        registrationMapper.insertPendingUser(
                username,
                passwordEncoder.encode(request.password()),
                realName,
                "RESIDENT".equals(accountType) ? null : phone,
                accountType,
                requestedResidentId,
                normalizeOptional(request.note())
        );
        return new RegistrationResponse(
                username,
                accountType,
                "PENDING",
                "注册申请已提交，管理员审核通过后即可登录"
        );
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
