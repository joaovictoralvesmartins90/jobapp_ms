package jvam90.review_service.messaging.producer;

import jvam90.review_service.messaging.dto.ReviewMessage;
import jvam90.review_service.models.Review;
import jvam90.review_service.services.IReviewService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReviewMessageProducer {
    private final RabbitTemplate rabbitTemplate;
    private final IReviewService reviewService;

    public ReviewMessageProducer(RabbitTemplate rabbitTemplate, IReviewService reviewService) {
        this.rabbitTemplate = rabbitTemplate;
        this.reviewService = reviewService;
    }

    public void sendMessage(Review review){
        ReviewMessage reviewMessage = new ReviewMessage();
        reviewMessage.setId(review.getId());
        reviewMessage.setTitle(review.getTitle());
        reviewMessage.setDescription(review.getDescription());
        reviewMessage.setRating(reviewService.getAverageRating(review.getCompanyId()));
        reviewMessage.setCompanyId(review.getCompanyId());
        rabbitTemplate.convertAndSend("companyRatingQueue", reviewMessage);
    }
}
