package io.github.matijaradovic.spona.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import io.github.matijaradovic.spona.storage.StorageService;
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

    private final StorageService storage = mock(StorageService.class);

    @BeforeEach
    void storeCvs() {
        when(storage.putCv(any(), any())).thenAnswer(call -> UUID.randomUUID() + ".pdf");
    }

    @Test
    void staysOffUnderTests() {
        assertThat(context.getBeansOfType(DevSeeder.class)).isEmpty();
    }

    @Test
    void writesOnceAndEverySeededAccountSignsIn() {
        DevSeeder seeder = seeder();

        assertThat(seeder.seed()).isTrue();
        assertThat(seeder.seed()).isFalse();

        List<String> digests = jdbc.queryForList(
                "select password_digest from app_user where email like ? and email_verified_at is not null",
                String.class, "%" + DevSeeder.EMAIL_DOMAIN);
        assertThat(digests).hasSize(40)
                .allSatisfy(digest -> assertThat(encoder.matches(DevSeeder.PASSWORD, digest)).isTrue());
    }

    @Test
    void seedsEveryKindOfRow() {
        seeder().seed();

        assertThat(count("select count(*) from organization where id::text like '5eed0002-%'")).isEqualTo(13);
        assertThat(count("select count(*) from role_assignment where role = 'OWNER' and revoked_at is null"
                + " and organization_id::text like '5eed0002-%'")).isEqualTo(13);
        assertThat(count("select count(*) from post where id::text like '5eed0003-%'")).isEqualTo(89);
        assertThat(count("select count(*) from reaction r join comment c on c.id = r.target_id"
                + " where c.id::text like '5eed0004-%'")).isEqualTo(190);
        assertThat(count("select count(*) from job where status = 'OPEN' and id::text like '5eed0005-%'"
                + " and search_vector @@ websearch_to_tsquery('sr', 'programer')")).isEqualTo(3);
    }

    @Test
    void storesItsOwnCopyOfTheApplicantsCvForEveryApplication() {
        seeder().seed();

        ArgumentCaptor<byte[]> files = ArgumentCaptor.forClass(byte[].class);
        verify(storage, times(46)).putCv(files.capture(), eq("application/pdf"));
        assertThat(files.getAllValues())
                .allSatisfy(file -> assertThat(file).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII)));
        assertThat(count("select count(distinct cv_object_key) from job_application where id::text like '5eed0006-%'"))
                .isEqualTo(46);
        assertThat(count("select count(*) from job_application where cv_object_key = '" + DevSeeder.CV_PLACEHOLDER + "'"))
                .isZero();
    }

    private DevSeeder seeder() {
        return new DevSeeder(dataSource, jdbc, encoder, transactionManager, storage);
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
