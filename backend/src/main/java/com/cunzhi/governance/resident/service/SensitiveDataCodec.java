package com.cunzhi.governance.resident.service;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class SensitiveDataCodec {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final String base64Key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SensitiveDataCodec(AppProperties properties) {
        this.base64Key = properties.security().dataEncryptionKey();
    }

    public Encoded encode(String normalizedPlaintext) {
        if (normalizedPlaintext == null || normalizedPlaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(normalizedPlaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return new Encoded(payload, sha256(normalizedPlaintext), last4(normalizedPlaintext));
        } catch (GeneralSecurityException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "敏感数据加密失败");
        }
    }

    public String decrypt(byte[] payload) {
        if (payload == null) {
            return null;
        }
        if (payload.length <= IV_LENGTH) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "敏感数据密文格式无效");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "敏感数据解密失败");
        }
    }

    public String fingerprint(String normalizedPlaintext) {
        if (normalizedPlaintext == null || normalizedPlaintext.isBlank()) {
            return null;
        }
        return sha256(normalizedPlaintext);
    }

    private SecretKey secretKey() {
        if (base64Key == null || base64Key.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "未配置 DATA_ENCRYPTION_KEY");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("AES-256 requires 32 bytes");
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "DATA_ENCRYPTION_KEY 必须是32字节Base64密钥");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "系统不支持SHA-256");
        }
    }

    private String last4(String value) {
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }

    public record Encoded(byte[] ciphertext, String hash, String last4) {
        public Encoded {
            ciphertext = ciphertext.clone();
        }

        @Override
        public byte[] ciphertext() {
            return ciphertext.clone();
        }
    }
}
