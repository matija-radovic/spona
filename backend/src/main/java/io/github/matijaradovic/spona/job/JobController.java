package io.github.matijaradovic.spona.job;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.matijaradovic.spona.auth.jwt.CurrentUser;
import io.github.matijaradovic.spona.common.web.PageMapper;
import io.github.matijaradovic.spona.common.web.PagedResponse;
import io.github.matijaradovic.spona.job.dto.JobDetailDto;
import io.github.matijaradovic.spona.job.dto.JobListItemDto;
import io.github.matijaradovic.spona.job.dto.JobListQuery;
import io.github.matijaradovic.spona.job.dto.SavedJobItemDto;
import io.github.matijaradovic.spona.user.User;

@RequestMapping("/jobs")
@RestController
public class JobController {

    private static final String DEFAULT_SIZE = "" + PageMapper.DEFAULT_SIZE;

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    PagedResponse<JobListItemDto> list(JobListQuery query, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = DEFAULT_SIZE) int size) {
        return PageMapper.toResponse(jobService.list(query, PageMapper.toPageable(page, size)));
    }

    /** Ids and statuses each arrive comma-joined. */
    @GetMapping(params = "id")
    List<SavedJobItemDto> saved(@RequestParam String id, @RequestParam(required = false) String status) {
        return jobService.saved(id, status);
    }

    @GetMapping("/{slug}")
    JobDetailDto job(@PathVariable String slug, @CurrentUser User viewer) {
        return jobService.job(slug, viewer);
    }
}
