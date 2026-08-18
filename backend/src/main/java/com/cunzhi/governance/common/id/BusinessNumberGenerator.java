package com.cunzhi.governance.common.id;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class BusinessNumberGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private final SecureRandom random = new SecureRandom();
    private final Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public String next(String prefix) {
        int suffix = random.nextInt(1_000_000);
        return prefix + LocalDateTime.now(clock).format(FORMATTER) + "%06d".formatted(suffix);
    }
}
