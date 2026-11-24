package jvam90.job_service.dtos;

public record JobResponse(
        Long id,
        String title,
        String description,
        String minSalary,
        String maxSalary,
        String location,
        CompanyResponse companyResponse) {
}
