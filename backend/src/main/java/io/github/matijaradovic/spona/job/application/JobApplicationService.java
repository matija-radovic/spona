package io.github.matijaradovic.spona.job.application;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import io.github.matijaradovic.spona.actor.ContentAuthority;
import io.github.matijaradovic.spona.common.error.ConflictException;
import io.github.matijaradovic.spona.common.error.NotFoundException;
import io.github.matijaradovic.spona.common.error.UnprocessableException;
import io.github.matijaradovic.spona.common.util.TransactionHooks;
import io.github.matijaradovic.spona.job.ApplicationType;
import io.github.matijaradovic.spona.job.Job;
import io.github.matijaradovic.spona.job.JobRepository;
import io.github.matijaradovic.spona.job.JobService;
import io.github.matijaradovic.spona.job.JobStatus;
import io.github.matijaradovic.spona.job.dto.JobApplicationDto;
import io.github.matijaradovic.spona.storage.StorageService;
import io.github.matijaradovic.spona.user.User;
import io.github.matijaradovic.spona.user.UserRepository;

@Service
public class JobApplicationService {

    /**pdf file signature */
    private static final byte[] PDF_MAGIC = { '%', 'P', 'D', 'F', '-' };
    private static final String PDF = "application/pdf";

    private static final Logger log = LoggerFactory.getLogger(JobApplicationService.class);

    private final JobApplicationRepository applications;
    private final JobRepository jobs;
    private final JobService jobService;
    private final UserRepository users;
    private final StorageService storage;
    private final ContentAuthority authority;

    public JobApplicationService(JobApplicationRepository applications, JobRepository jobs, JobService jobService,
            UserRepository users, StorageService storage, ContentAuthority authority) {
        this.applications = applications;
        this.jobs = jobs;
        this.jobService = jobService;
        this.users = users;
        this.storage = storage;
        this.authority = authority;
    }

    @Transactional
    public JobApplicationDto submit(UUID jobId, User caller, MultipartFile cv) {
        byte[] bytes = pdf(cv);
        Job job = jobs.findWithCreatedFor(jobId).orElseThrow(() -> notFound());
        if (job.getStatus() == JobStatus.DRAFT) {
            if (!authority.canManageJobPostings(job.getCreatedFor(), caller)) {
                throw notFound();
            }
            throw new ConflictException("This posting is still a draft. Publish it before it can take applications.");
        }
        if (job.getStatus() == JobStatus.CLOSED) {
            throw new ConflictException("This posting is closed and takes no more applications.");
        }
        if (job.getApplicationType() != ApplicationType.CV_DROP) {
            throw new ConflictException("This posting is not applied to with a CV.");
        }
        String key = storage.putCv(bytes, PDF);
        TransactionHooks.afterRollback(() -> delete(key));
        JobApplication application = applications
                .saveAndFlush(new JobApplication(job, users.getReferenceById(caller.getId()), key));
        return JobApplicationDto.from(application, storage.presignCv(key));
    }

    @Transactional(readOnly = true)
    public Page<JobApplicationDto> inbox(UUID jobId, User caller, Pageable pageable) {
        jobService.editable(jobId, caller, "You may not read this posting's applications.");
        return applications.findInbox(jobId, pageable)
                .map(row -> JobApplicationDto.from(row, storage.presignCv(row.getCvObjectKey())));
    }

    private static byte[] pdf(MultipartFile cv) {
        if (cv == null || cv.isEmpty()) {
            throw UnprocessableException.on("cv", "Attach your CV as a PDF file.");
        }
        byte[] bytes;
        try {
            bytes = cv.getBytes();
        } catch (IOException ex) {
            throw UnprocessableException.on("cv", "Your CV could not be read. Try sending it again.");
        }
        if (bytes.length < PDF_MAGIC.length) {
            throw UnprocessableException.on("cv", "Your CV has to be a PDF file.");
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                throw UnprocessableException.on("cv", "Your CV has to be a PDF file.");
            }
        }
        return bytes;
    }

    private void delete(String key) {
        try {
            storage.deleteCv(key);
        } catch (RuntimeException ex) {
            log.warn("CV {} was not deleted: {}", key, ex.getMessage());
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Job not found.");
    }
}
