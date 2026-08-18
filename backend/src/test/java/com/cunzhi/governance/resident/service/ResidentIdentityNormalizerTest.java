package com.cunzhi.governance.resident.service;

import com.cunzhi.governance.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResidentIdentityNormalizerTest {

    @Test
    void normalizesEquivalentPhoneRepresentations() {
        assertThat(ResidentIdentityNormalizer.normalizePhone(" 138-0000 0000 "))
                .isEqualTo("13800000000");
        assertThat(ResidentIdentityNormalizer.normalizePhone("+86 138-0000-0000"))
                .isEqualTo("+8613800000000");
    }

    @Test
    void normalizesIdCardCheckCharacter() {
        assertThat(ResidentIdentityNormalizer.normalizeIdCard(" 11010119900101123x "))
                .isEqualTo("11010119900101123X");
    }

    @Test
    void rejectsPhoneWithUnsupportedCharacters() {
        assertThatThrownBy(() -> ResidentIdentityNormalizer.normalizePhone("138.0000.0000"))
                .isInstanceOf(BusinessException.class);
    }
}
