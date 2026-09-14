package io.github.matijaradovic.spona.job.application;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.matijaradovic.spona.auth.jwt.CurrentUser;
import io.github.matijaradovic.spona.common.web.PageMapper;
import io.github.matijaradovic.spona.common.web.PagedResponse;
import io.github.matijaradovic.spona.job.dto.JobApplicationDto;
import io.github.matijaradovic.spona.user.User;

@RequestMapping("/jobs/{jobId}/applications")
@RestController
public class JobApplicationController {

    private static final String DEFAULT_SIZE = "" + PageMapper.DEFAULT_SIZE;

    private final JobApplicationService applications;

    public JobApplicationController(JobApplicationService applications) {
        this.applications = applications;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    JobApplicationDto submit(@PathVariable UUID jobId, @CurrentUser User caller,
            @RequestParam(name = "cv", required = false) MultipartFile cv) {
        return applications.submit(jobId, caller, cv);
    }

    @GetMapping
    PagedResponse<JobApplicationDto> inbox(@PathVariable UUID jobId, @CurrentUser User caller,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = DEFAULT_SIZE) int size) {
        return PageMapper.toResponse(applications.inbox(jobId, caller, PageMapper.toPageable(page, size)));
    }
}
