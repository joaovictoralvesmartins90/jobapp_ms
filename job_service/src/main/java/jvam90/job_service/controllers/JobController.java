package jvam90.job_service.controllers;

import jakarta.validation.Valid;
import jvam90.job_service.dtos.JobRequest;
import jvam90.job_service.dtos.JobResponse;
import jvam90.job_service.services.IJobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}/jobs")
public class JobController {

    private final IJobService jobService;

    public JobController(IJobService jobService){
        this.jobService = jobService;
    }

    @GetMapping()
    public ResponseEntity<List<JobResponse>> findAll(@PathVariable Long companyId){
        return new ResponseEntity<>(this.jobService.findAll(companyId), HttpStatus.OK) ;
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> findJobById(@PathVariable Long companyId, @PathVariable Long jobId){
        return ResponseEntity.ok(jobService.findJobById(companyId, jobId));
    }

    @PostMapping()
    public ResponseEntity<JobResponse> addJob(@RequestBody @Valid JobRequest jobRequest, @PathVariable Long companyId){
        JobResponse jobCreated = this.jobService.addJob(jobRequest, companyId);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(jobCreated.id())
                .toUri();
        return ResponseEntity.created(location).body(jobCreated);
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> deleteJob(@PathVariable Long companyId, @PathVariable Long jobId){
        jobService.deleteJob(companyId, jobId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{jobId}")
    public ResponseEntity<Void> updateJob(@RequestBody JobRequest jobRequest, @PathVariable Long companyId, @PathVariable Long jobId){
        jobService.updateJob(jobRequest, companyId, jobId);
        return ResponseEntity.noContent().build();
    }

}
