package jvam90.job_service.clients;

import jvam90.job_service.dtos.CompanyDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "company-service")
public interface CompanyClient {
    @GetMapping("/companies/{companyId}")
    CompanyDto getCompany(@PathVariable Long companyId);
}
