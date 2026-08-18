package com.cunzhi.governance.system.bootstrap;

import com.cunzhi.governance.config.AppProperties;
import com.cunzhi.governance.system.mapper.InitialAdminMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InitialAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminBootstrap.class);

    private final AppProperties properties;
    private final InitialAdminMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public InitialAdminBootstrap(
            AppProperties properties,
            InitialAdminMapper mapper,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = properties.bootstrap().admin();
        if (!admin.enabled()) {
            return;
        }
        if (admin.password() == null || admin.password().isBlank()) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_ENABLED=true 时必须提供 BOOTSTRAP_ADMIN_PASSWORD"
            );
        }
        if (admin.password().length() < 12) {
            throw new IllegalStateException("首个管理员密码长度不能少于 12 位");
        }
        if (admin.username() == null || admin.username().isBlank() || admin.username().length() > 64) {
            throw new IllegalStateException("首个管理员用户名不能为空且不能超过 64 个字符");
        }
        if (admin.realName() == null || admin.realName().isBlank() || admin.realName().length() > 80) {
            throw new IllegalStateException("首个管理员姓名不能为空且不能超过 80 个字符");
        }
        Long roleId = mapper.lockSystemAdminRole();
        if (roleId == null) {
            throw new IllegalStateException("SYSTEM_ADMIN 角色尚未初始化");
        }
        if (mapper.countSystemAdminUsers() > 0) {
            log.info("Initial administrator bootstrap skipped: an administrator already exists");
            return;
        }
        if (mapper.findUserIdByUsername(admin.username()) != null) {
            throw new IllegalStateException("Bootstrap username already exists without SYSTEM_ADMIN role");
        }

        mapper.insertUser(
                admin.username(),
                passwordEncoder.encode(admin.password()),
                admin.realName()
        );
        Long userId = mapper.findUserIdByUsername(admin.username());
        mapper.insertUserRole(userId, roleId);
        log.info("Initial administrator created for username '{}'; bootstrap should now be disabled",
                admin.username());
    }
}
