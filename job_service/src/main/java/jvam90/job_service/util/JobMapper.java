package jvam90.job_service.util;

import jvam90.job_service.dtos.CreateJobDto;
import jvam90.job_service.models.Job;
import org.springframework.stereotype.Component;

@Component
public class JobMapper {
    public static Job mapFromDto(CreateJobDto createJobDto){
        Job mappedJob = new Job();
        mappedJob.setDescription(createJobDto.getDescription());
        mappedJob.setLocation(createJobDto.getLocation());
        mappedJob.setTitle(createJobDto.getTitle());
        mappedJob.setMinSalary(createJobDto.getMinSalary());
        mappedJob.setMaxSalary(createJobDto.getMaxSalary());
        mappedJob.setCompanyId(createJobDto.getCompanyId());
        return mappedJob;
    }

    public static CreateJobDto mapFromClass(Job job){
        CreateJobDto mappedJob = new CreateJobDto();
        mappedJob.setId(job.getId());
        mappedJob.setDescription(job.getDescription());
        mappedJob.setLocation(job.getLocation());
        mappedJob.setTitle(job.getTitle());
        mappedJob.setMinSalary(job.getMinSalary());
        mappedJob.setMaxSalary(job.getMaxSalary());
        mappedJob.setCompanyId(job.getCompanyId());
        return mappedJob;
    }
}
