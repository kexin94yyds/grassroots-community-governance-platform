package com.cunzhi.governance.resident.service;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveDataCodecTest {

    @Test
    void encryptsWithRandomIvAndKeepsStableHashAndLastFour() {
        SensitiveDataCodec codec = new SensitiveDataCodec(properties(validKey()));

        SensitiveDataCodec.Encoded first = codec.encode("110101199001011234");
        SensitiveDataCodec.Encoded second = codec.encode("110101199001011234");

        assertThat(codec.decrypt(first.ciphertext())).isEqualTo("110101199001011234");
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(first.hash()).isEqualTo(second.hash()).hasSize(64);
        assertThat(first.last4()).isEqualTo("1234");
    }

    @Test
    void rejectsMissingEncryptionKeyWhenSensitiveValueIsWritten() {
        SensitiveDataCodec codec = new SensitiveDataCodec(properties(""));

        assertThatThrownBy(() -> codec.encode("13800138000"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void rejectsTamperedCiphertext() {
        SensitiveDataCodec codec = new SensitiveDataCodec(properties(validKey()));
        byte[] ciphertext = codec.encode("13800138000").ciphertext();
        ciphertext[ciphertext.length - 1] ^= 1;

        assertThatThrownBy(() -> codec.decrypt(ciphertext))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    private String validKey() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }

    private AppProperties properties(String encryptionKey) {
        return new AppProperties(
                new AppProperties.Security(List.of(), encryptionKey),
                new AppProperties.Attachment("./data", 1024, List.of()),
                new AppProperties.Bootstrap(
                        new AppProperties.Admin(false, "admin", "", "系统管理员")
                )
        );
    }
}
