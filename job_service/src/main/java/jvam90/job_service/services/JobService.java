package jvam90.job_service.services;

import jvam90.job_service.dtos.CompanyDto;
import jvam90.job_service.dtos.CreateJobDto;
import jvam90.job_service.dtos.JobDto;
import jvam90.job_service.dtos.JobWithCompanyDto;
import jvam90.job_service.models.Job;
import jvam90.job_service.repositories.JobRepository;
import jvam90.job_service.util.JobMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class JobService implements IJobService {

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public List<JobWithCompanyDto> findAll(Long companyId) {
        List<Job> jobs = jobRepository.findByCompanyId(companyId);
        List<JobWithCompanyDto> jobWithCompanyDtos = new ArrayList<>();
        RestTemplate restTemplate = new RestTemplate();

        for(Job job : jobs){
            CompanyDto companyDto = restTemplate.getForObject("http://localhost:8081/companies/" + job.getCompanyId(), CompanyDto.class);
            JobWithCompanyDto jobWithCompanyDto = getJobWithCompanyDto(job, companyDto);
            jobWithCompanyDtos.add(jobWithCompanyDto);
        }
        return jobWithCompanyDtos;
    }

    private static @NonNull JobWithCompanyDto getJobWithCompanyDto(Job job, CompanyDto companyDto) {
        JobDto jobDto = new JobDto();
        jobDto.setId(job.getId());
        jobDto.setTitle(job.getTitle());
        jobDto.setDescription(job.getDescription());
        jobDto.setLocation(job.getLocation());
        jobDto.setMinSalary(job.getMinSalary());
        jobDto.setMaxSalary(job.getMaxSalary());
        jobDto.setCompanyId(job.getCompanyId());
        JobWithCompanyDto jobWithCompanyDto = new JobWithCompanyDto();
        jobWithCompanyDto.setCompanyDto(companyDto);
        jobWithCompanyDto.setJobDto(jobDto);
        return jobWithCompanyDto;
    }

    @Override
    public Optional<Job> findJobById(Long jobId) {
        return jobRepository.findById(jobId)
                .stream()
                .filter(job -> job.getId().equals(jobId))
                .findFirst();
    }

    @Override
    public Job addJob(CreateJobDto createJobDto, Long companyId) {
        createJobDto.setCompanyId(companyId);
        Job mappedJob = JobMapper.mapFromDto(createJobDto);
        return jobRepository.save(mappedJob);
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
