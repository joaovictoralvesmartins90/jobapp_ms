package jvam90.company_service.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "review-service")
public interface ReviewClient {
    @GetMapping("/companies/{companyId}/reviews/averagerating")
    Double getAverageRating(@PathVariable Long companyId);
}
