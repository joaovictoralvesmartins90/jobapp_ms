package jvam90.job_service.controllers;

import jvam90.job_service.dtos.CreateJobDto;
import jvam90.job_service.dtos.JobWithCompanyDto;
import jvam90.job_service.models.Job;
import jvam90.job_service.services.IJobService;
import jvam90.job_service.util.JobMapper;
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
    public ResponseEntity<List<JobWithCompanyDto>> findAll(@PathVariable Long companyId){
        return new ResponseEntity<>(this.jobService.findAll(companyId), HttpStatus.OK) ;
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobWithCompanyDto> findJobById(@PathVariable Long companyId, @PathVariable Long jobId){
        JobWithCompanyDto jobWithCompanyDto = this.jobService.findJobById(jobId);
        if(jobWithCompanyDto == null){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(jobWithCompanyDto);
    }

    @PostMapping()
    public ResponseEntity<CreateJobDto> addJob(@RequestBody CreateJobDto createJobDto, @PathVariable Long companyId){
        Job jobCreated = this.jobService.addJob(createJobDto, companyId);
        CreateJobDto mappedDto = JobMapper.mapFromClass(jobCreated);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(jobCreated.getId())
                .toUri();
        return ResponseEntity.created(location).body(mappedDto);
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<String> deleteJob(@PathVariable Long companyId, @PathVariable Long jobId){
        if(this.jobService.deleteJob(jobId)){
            return ResponseEntity.ok("Job deleted succesfully");
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{jobId}")
    public ResponseEntity<String> updateJob(@RequestBody Job job, @PathVariable Long companyId, @PathVariable Long jobId){
        if(this.jobService.updateJob(job, jobId)){
            return ResponseEntity.ok("Job updated succesfully");
        }
        return ResponseEntity.notFound().build();
    }

}
