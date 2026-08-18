package com.cunzhi.governance.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({
        "com.cunzhi.governance.auth.mapper",
        "com.cunzhi.governance.attachment.mapper",
        "com.cunzhi.governance.system.mapper",
        "com.cunzhi.governance.grid.mapper",
        "com.cunzhi.governance.resident.mapper",
        "com.cunzhi.governance.event.mapper",
        "com.cunzhi.governance.task.mapper",
        "com.cunzhi.governance.dashboard.mapper",
        "com.cunzhi.governance.insight.mapper"
})
public class MyBatisConfig {
}
