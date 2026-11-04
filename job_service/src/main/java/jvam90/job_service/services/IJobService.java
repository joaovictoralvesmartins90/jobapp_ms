package jvam90.job_service.services;

import jvam90.job_service.dtos.JobWithCompanyDto;
import jvam90.job_service.models.Job;

import java.util.List;
import java.util.Optional;

public interface IJobService {
    List<JobWithCompanyDto> findAll(Long companyId);
    Optional<Job> findJobById(Long jobId);
    Job addJob(Job job, Long companyId);
    boolean deleteJob(Long id);
    boolean updateJob(Job job, Long jobId);
}
