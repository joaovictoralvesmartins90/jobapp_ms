package jvam90.job_service.services;

import jvam90.job_service.dtos.JobRequest;
import jvam90.job_service.dtos.JobResponse;
import jvam90.job_service.models.Job;

import java.util.List;

public interface IJobService {
    List<JobResponse> findAll(Long companyId);
    JobResponse findJobById(Long companyId, Long jobId);
    JobResponse addJob(JobRequest job, Long companyId);
    void deleteJob(Long companyId, Long id);
    void updateJob(JobRequest job, Long companyId, Long jobId);
}
