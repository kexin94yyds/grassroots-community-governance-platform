package com.cunzhi.governance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Security security,
        Attachment attachment,
        Bootstrap bootstrap
) {
    public record Security(List<String> allowedOrigins, String dataEncryptionKey) {
        public Security {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    public record Attachment(
            String storageRoot,
            long maxFileSizeBytes,
            List<String> allowedContentTypes
    ) {
        public Attachment {
            allowedContentTypes = allowedContentTypes == null ? List.of() : List.copyOf(allowedContentTypes);
        }
    }

    public record Bootstrap(Admin admin) {
    }

    public record Admin(
            boolean enabled,
            String username,
            String password,
            String realName
    ) {
    }
}
