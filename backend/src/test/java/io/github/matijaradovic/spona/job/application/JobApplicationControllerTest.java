package io.github.matijaradovic.spona.job.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import io.github.matijaradovic.spona.common.domain.ActorType;
import io.github.matijaradovic.spona.common.dto.ActorRefDto;
import io.github.matijaradovic.spona.common.web.GlobalExceptionHandler;
import io.github.matijaradovic.spona.common.web.PageMapper;
import io.github.matijaradovic.spona.job.dto.JobApplicationDto;
import io.github.matijaradovic.spona.support.TestFixtures;
import io.github.matijaradovic.spona.user.User;

class JobApplicationControllerTest {

    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    private static final UUID ROW_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b3");
    private static final Instant SUBMITTED = Instant.parse("2026-02-02T10:00:00Z");
    private static final byte[] PDF = "%PDF-1.7 nesto".getBytes(StandardCharsets.UTF_8);

    private final JobApplicationService applications = mock(JobApplicationService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new JobApplicationController(applications))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    private final User caller = TestFixtures.userWithId("caller", CALLER_ID);

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theCvPartAndTheCallerReachTheServiceAndASubmitIs201() throws Exception {
        signIn();
        when(applications.submit(eq(JOB_ID), eq(caller), any())).thenReturn(row());

        mvc.perform(multipart("/jobs/" + JOB_ID + "/applications")
                .file(new MockMultipartFile("cv", "zivotopis.pdf", "application/pdf", PDF)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ROW_ID.toString()))
                .andExpect(jsonPath("$.cvUrl").value("https://minio/signed"))
                .andExpect(jsonPath("$.submittedAt").value("2026-02-02T10:00:00Z"))
                .andExpect(jsonPath("$.applicant.handle").value("caller"));

        ArgumentCaptor<MultipartFile> sent = ArgumentCaptor.forClass(MultipartFile.class);
        verify(applications).submit(eq(JOB_ID), eq(caller), sent.capture());
        assertThat(sent.getValue().getBytes()).isEqualTo(PDF);
        assertThat(sent.getValue().getOriginalFilename()).isEqualTo("zivotopis.pdf");
    }

    @Test
    void aSubmitWithNoCvPartStillReachesTheServiceAsNull() throws Exception {
        signIn();

        mvc.perform(multipart("/jobs/" + JOB_ID + "/applications"));

        verify(applications).submit(JOB_ID, caller, null);
    }

    @Test
    void thePageAndSizeReachTheServiceAndTheInboxIsAPagedEnvelope() throws Exception {
        signIn();
        Pageable asked = PageMapper.toPageable(2, 5);
        when(applications.inbox(JOB_ID, caller, asked)).thenReturn(new PageImpl<>(List.of(row()), asked, 11));

        mvc.perform(get("/jobs/" + JOB_ID + "/applications").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.content[0].cvUrl").value("https://minio/signed"));
    }

    @Test
    void theInboxDefaultsToTheFirstPage() throws Exception {
        signIn();
        Pageable asked = PageMapper.toPageable(1, PageMapper.DEFAULT_SIZE);
        when(applications.inbox(JOB_ID, caller, asked)).thenReturn(new PageImpl<>(List.of(), asked, 0));

        mvc.perform(get("/jobs/" + JOB_ID + "/applications")).andExpect(status().isOk());

        verify(applications).inbox(JOB_ID, caller, asked);
    }

    private void signIn() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(caller, null, List.of()));
    }

    private static JobApplicationDto row() {
        return new JobApplicationDto(ROW_ID,
                new ActorRefDto(ActorType.USER, CALLER_ID, "Ime Prezime", "caller", null),
                "https://minio/signed", SUBMITTED);
    }
}
