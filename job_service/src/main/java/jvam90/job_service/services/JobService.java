package jvam90.job_service.services;

import jvam90.job_service.clients.CompanyClient;
import jvam90.job_service.dtos.*;
import jvam90.job_service.exceptions.CompanyNotFoundException;
import jvam90.job_service.exceptions.JobNotFoundException;
import jvam90.job_service.models.Job;
import jvam90.job_service.repositories.JobRepository;
import jvam90.job_service.util.JobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class JobService implements IJobService {

    private final JobRepository jobRepository;
    private final CompanyClient companyClient;

    public JobService(JobRepository jobRepository, CompanyClient companyClient) {
        this.jobRepository = jobRepository;
        this.companyClient = companyClient;
    }

    @Override
    public List<JobResponse> findAll(Long companyId) {
        List<Job> jobs = jobRepository.findByCompanyId(companyId);
        List<JobResponse> jobResponses = new ArrayList<>();

        for(Job job : jobs){
            CompanyResponse companyResponse = companyClient.getCompany(job.getCompanyId());
            JobResponse jobWithCompanyResponse = new JobResponse(job.getId(), job.getTitle(), job.getDescription(),
                    job.getMinSalary(), job.getMaxSalary(), job.getLocation(), companyResponse);
            jobResponses.add(jobWithCompanyResponse);
        }
        return jobResponses;
    }

    @Override
    public JobResponse findJobById(Long companyId, Long jobId) {
        Job job = jobRepository.findById(jobId)
                .stream()
                .filter(j -> j.getId().equals(jobId) && j.getCompanyId().equals(companyId))
                .findFirst().orElse(null);
        if(job == null){
            throw new JobNotFoundException(jobId.toString());
        }
        CompanyResponse companyResponse = companyClient.getCompany(job.getCompanyId());
        return new JobResponse(job.getId(), job.getTitle(), job.getDescription(),
                job.getMinSalary(), job.getMaxSalary(), job.getLocation(), companyResponse);
    }

    @Override
    public JobResponse addJob(JobRequest createJobDto, Long companyId) {
        CompanyResponse companyResponse = companyClient.getCompany(companyId);
        if(companyResponse == null){
            throw new CompanyNotFoundException(companyId.toString());
        }
        Job mappedJob = JobMapper.mapFromRequest(createJobDto);
        jobRepository.save(mappedJob);
        return JobMapper.mapFromClass(mappedJob, companyResponse);
    }

    @Override
    @Transactional
    public void deleteJob(Long companyId, Long jobId) {
        Job job = getJob(companyId, jobId);
        if (job == null) throw new JobNotFoundException(jobId.toString());
        this.jobRepository.delete(job);
    }

    private Job getJob(Long companyId, Long jobId) {
        return this.jobRepository.findById(jobId)
                .stream()
                .filter(j -> j.getCompanyId().equals(companyId) && j.getId().equals(jobId))
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional
    public void updateJob(JobRequest jobRequest, Long companyId, Long jobId) {
        Job jobDb = getJob(companyId, jobId);
        if (jobDb == null) throw new JobNotFoundException(jobId.toString());
        jobDb.setTitle(jobRequest.title());
        jobDb.setDescription(jobRequest.description());
        jobDb.setMinSalary(jobRequest.minSalary());
        jobDb.setMaxSalary(jobRequest.maxSalary());
        jobDb.setLocation(jobRequest.location());
    }
}
