package io.github.matijaradovic.spona.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.support.ContainersConfig;

@SpringBootTest
@Import(ContainersConfig.class)
@Transactional
class DevSeederIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void staysOffUnderTests() {
        assertThat(context.getBeansOfType(DevSeeder.class)).isEmpty();
    }

    @Test
    void writesOnceAndEverySeededAccountSignsIn() {
        DevSeeder seeder = new DevSeeder(dataSource, jdbc, encoder, transactionManager);

        assertThat(seeder.seed()).isTrue();
        assertThat(seeder.seed()).isFalse();

        List<String> digests = jdbc.queryForList(
                "select password_digest from app_user where email like ? and email_verified_at is not null",
                String.class, "%" + DevSeeder.EMAIL_DOMAIN);
        assertThat(digests).hasSize(8)
                .allSatisfy(digest -> assertThat(encoder.matches(DevSeeder.PASSWORD, digest)).isTrue());
    }

    @Test
    void seedsEveryKindOfRow() {
        new DevSeeder(dataSource, jdbc, encoder, transactionManager).seed();

        assertThat(count("select count(*) from organization where slug in ('kodnest', 'zelena-basta', 'klinika-sunce')"))
                .isEqualTo(3);
        assertThat(count("select count(*) from role_assignment where role = 'OWNER' and revoked_at is null"
                + " and organization_id::text like '5eed0002-%'")).isEqualTo(3);
        assertThat(count("select count(*) from post where id::text like '5eed0003-%'")).isEqualTo(10);
        assertThat(count("select count(*) from reaction r join comment c on c.id = r.target_id"
                + " where c.id::text like '5eed0004-%'")).isEqualTo(4);
        assertThat(count("select count(*) from job where status = 'OPEN' and id::text like '5eed0005-%'"
                + " and search_vector @@ websearch_to_tsquery('sr', 'programer')")).isEqualTo(1);
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
