package jvam90.job_service.services;

import jvam90.job_service.models.Job;
import jvam90.job_service.repositories.JobRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class JobService implements IJobService {

    private final JobRepository jobRepository;
    //private final CompanyService companyService;

//    public JobService(JobRepository jobRepository, CompanyService companyService) {
//        this.jobRepository = jobRepository;
//        this.companyService = companyService;
//    }

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
    public Job addJob(Job job) {
        //Optional<Company> company = companyService.findCompanyById(companyId);
        //job.setCompany(company.get());
        return jobRepository.save(job);
    }

    @Override
    public boolean deleteJob(Long jobId) {
        Job job = getJob(jobId);
        if (job == null) return false;
        this.jobRepository.delete(job);
        return true;
    }

//    private Job getJob(Long companyId, Long jobId) {
//        Optional<Company> company = companyService.findCompanyById(companyId);
//        if(company.isEmpty()){
//            return null;
//        }
//        Optional<Job> job = this.jobRepository.findById(jobId);
//        return job.orElse(null);
//    }

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
        jobRepository.save(jobDb);
        return true;
    }

//    public boolean updateJob(Job job, Long companyId, Long jobId) {
//        Job jobDb = getJob(companyId, jobId);
//        if (jobDb == null) return false;
//        jobDb.setTitle(job.getTitle());
//        jobDb.setDescription(job.getDescription());
//        jobDb.setMinSalary(job.getMinSalary());
//        jobDb.setMaxSalary(job.getMaxSalary());
//        jobDb.setLocation(job.getLocation());
//        jobRepository.save(jobDb);
//        return true;
//    }

}
