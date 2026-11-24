package jvam90.job_service.dtos;

public record JobRequest(String title,
        String description,
        String minSalary,
        String maxSalary,
        String location,
        Long companyId) {
}
