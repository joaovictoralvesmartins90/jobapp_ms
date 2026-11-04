package jvam90.job_service.dtos;

public class JobWithCompanyDto {
    private JobDto jobDto;
    private CompanyDto companyDto;

    public JobDto getJobDto() {
        return jobDto;
    }

    public void setJobDto(JobDto jobDto) {
        this.jobDto = jobDto;
    }

    public CompanyDto getCompanyDto() {
        return companyDto;
    }

    public void setCompanyDto(CompanyDto companyDto) {
        this.companyDto = companyDto;
    }
}
