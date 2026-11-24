package jvam90.company_service.messaging;

import jvam90.company_service.messaging.dto.ReviewMessage;
import jvam90.company_service.services.CompanyService;
import jvam90.company_service.services.ICompanyService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class ReviewMessageConsumer {
    private final ICompanyService companyService;

    public ReviewMessageConsumer(ICompanyService companyService) {
        this.companyService = companyService;
    }

    @RabbitListener(queues = "companyRatingQueue")
    public void consumeMessage(ReviewMessage reviewMessage){
        companyService.updateCompanyRating(reviewMessage);
    }


}
