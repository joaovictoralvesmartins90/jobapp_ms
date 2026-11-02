package jvam90.review_service.services;


import jvam90.review_service.models.Review;

import java.util.List;
import java.util.Optional;

public interface IReviewService {
    List<Review> findAll(Long companyId);
    Optional<Review> findById(Long reviewId);
    Review addReview(Review review, Long companyId);
    boolean deleteReview(Long reviewId);
    boolean updateReview(Review review, Long reviewId);
}
