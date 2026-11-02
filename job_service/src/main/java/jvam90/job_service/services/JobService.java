package jvam90.job_service.services;

import jvam90.job_service.models.Job;
import jvam90.job_service.repositories.JobRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class JobService implements IJobService {

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public List<Job> findAll(Long companyId) {
        return jobRepository.findByCompanyId(companyId);
    }

    @Override
    public Optional<Job> findJobById(Long jobId) {
        return jobRepository.findById(jobId)
                .stream()
                .filter(job -> job.getId().equals(jobId))
                .findFirst();
    }

    @Override
    public Job addJob(Job job, Long companyId) {
        job.setCompanyId(companyId);
        return jobRepository.save(job);
    }

    @Override
    public boolean deleteJob(Long jobId) {
        Job job = getJob(jobId);
        if (job == null) return false;
        this.jobRepository.delete(job);
        return true;
    }

    private Job getJob(Long jobId) {
        Optional<Job> job = this.jobRepository.findById(jobId);
        return job.orElse(null);
    }

    @Override
    public boolean updateJob(Job job, Long jobId) {
        Job jobDb = getJob(jobId);
        if (jobDb == null) return false;
        jobDb.setTitle(job.getTitle());
        jobDb.setDescription(job.getDescription());
        jobDb.setMinSalary(job.getMinSalary());
        jobDb.setMaxSalary(job.getMaxSalary());
        jobDb.setLocation(job.getLocation());
        jobDb.setCompanyId(job.getCompanyId());
        jobRepository.save(jobDb);
        return true;
    }
}
