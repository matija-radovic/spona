package io.github.matijaradovic.spona.seed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import io.github.matijaradovic.spona.common.util.TransactionHooks;
import io.github.matijaradovic.spona.storage.StorageService;

/**
 * Fills a dev database with demo actors, posts, jobs and applications, once. Every seeded
 * account is verified and signs in with {@link #PASSWORD}
 */
@Component
@Profile("dev")
@ConditionalOnBooleanProperty("app.seed.enabled")
class DevSeeder implements ApplicationRunner {

    static final String PASSWORD = "spona1234";
    static final String EMAIL_DOMAIN = "@seed.spona.local";
    static final String CV_PLACEHOLDER = "seed-cv";

    private static final String MARKER_USERNAME = "ana-petrovic";
    private static final String SCRIPT = "db/seed/dev-seed.sql";
    private static final String CV_DIRECTORY = "db/seed/cv/";
    private static final String PDF = "application/pdf";

    private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transactions;
    private final StorageService storage;

    DevSeeder(DataSource dataSource, JdbcTemplate jdbc, PasswordEncoder encoder,
            PlatformTransactionManager transactionManager, StorageService storage) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.transactions = new TransactionTemplate(transactionManager);
        this.storage = storage;
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
        int applications = uploadCvs();
        log.info("Dev seed written: {} accounts, {} applications, password '{}'", accounts, applications, PASSWORD);
        return true;
    }

    private int uploadCvs() {
        List<Map<String, Object>> rows = jdbc.queryForList("select a.id, u.username from job_application a"
                + " join app_user u on u.id = a.created_by_id where a.cv_object_key = ?", CV_PLACEHOLDER);
        Map<String, byte[]> files = new HashMap<>();
        for (Map<String, Object> row : rows) {
            byte[] cv = files.computeIfAbsent((String) row.get("username"),
                    username -> read(CV_DIRECTORY + username + ".pdf"));
            String key = storage.putCv(cv, PDF);
            TransactionHooks.afterRollback(() -> delete(key));
            jdbc.update("update job_application set cv_object_key = ? where id = ?", key, row.get("id"));
        }
        return rows.size();
    }

    private void delete(String key) {
        try {
            storage.deleteCv(key);
        } catch (RuntimeException ex) {
            log.warn("Seeded CV {} was not deleted: {}", key, ex.getMessage());
        }
    }

    private static byte[] read(String path) {
        try {
            return new ClassPathResource(path).getContentAsByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
