package io.github.matijaradovic.spona.seed;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fills a dev database with demo actors, posts and jobs, once. Every seeded
 * account is verified and signs in with {@link #PASSWORD}
 */
@Component
@Profile("dev")
@ConditionalOnBooleanProperty("app.seed.enabled")
class DevSeeder implements ApplicationRunner {

    static final String PASSWORD = "spona1234";
    static final String EMAIL_DOMAIN = "@seed.spona.local";

    private static final String MARKER_USERNAME = "ana-petrovic";
    private static final String SCRIPT = "db/seed/dev-seed.sql";

    private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transactions;

    DevSeeder(DataSource dataSource, JdbcTemplate jdbc, PasswordEncoder encoder,
            PlatformTransactionManager transactionManager) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        transactions.executeWithoutResult(status -> seed());
    }

    /** Answers false when the seed is already there. Expects a surrounding transaction */
    boolean seed() {
        Boolean present = jdbc.queryForObject(
                "select exists (select 1 from app_user where username = ?)", Boolean.class, MARKER_USERNAME);
        if (Boolean.TRUE.equals(present)) {
            log.debug("Dev seed already present, skipping");
            return false;
        }
        ResourceDatabasePopulator script = new ResourceDatabasePopulator(new ClassPathResource(SCRIPT));
        script.setSqlScriptEncoding("UTF-8");
        DatabasePopulatorUtils.execute(script, dataSource);
        int accounts = jdbc.update("update app_user set password_digest = ? where email like ?",
                encoder.encode(PASSWORD), "%" + EMAIL_DOMAIN);
        log.info("Dev seed written: {} accounts, password '{}'", accounts, PASSWORD);
        return true;
    }
}
