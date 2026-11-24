package jvam90.job_service.util;

import jvam90.job_service.clients.CompanyClient;
import jvam90.job_service.dtos.CompanyResponse;
import jvam90.job_service.dtos.JobRequest;
import jvam90.job_service.dtos.JobResponse;
import jvam90.job_service.models.Job;
import org.springframework.stereotype.Component;

@Component
public class JobMapper {

    public static Job mapFromRequest(JobRequest jobRequest){
        Job mappedJob = new Job();
        mappedJob.setDescription(jobRequest.description());
        mappedJob.setLocation(jobRequest.location());
        mappedJob.setTitle(jobRequest.title());
        mappedJob.setMinSalary(jobRequest.minSalary());
        mappedJob.setMaxSalary(jobRequest.maxSalary());
        mappedJob.setCompanyId(jobRequest.companyId());
        return mappedJob;
    }

    public static JobResponse mapFromClass(Job job, CompanyResponse companyResponse){
        JobResponse jobResponse = new JobResponse(job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getMinSalary(),
                job.getMaxSalary(),
                job.getLocation(),
                companyResponse);
        return jobResponse;
    }

}
