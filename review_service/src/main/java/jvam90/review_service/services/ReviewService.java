package jvam90.review_service.services;

import jvam90.review_service.models.Review;
import jvam90.review_service.repositories.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ReviewService implements IReviewService{

    private final ReviewRepository reviewRepository;
    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Override
    public List<Review> findAll(Long companyId) {
        return reviewRepository.findByCompanyId(companyId);
    }

    @Override
    public Optional<Review> findById(Long reviewId) {
        return reviewRepository.findById(reviewId);
    }

    @Override
    public Review addReview(Review review, Long companyId) {
        review.setCompanyId(companyId);
        return reviewRepository.save(review);
    }


    @Override
    public boolean deleteReview(Long reviewId) {
        Optional<Review> review = reviewRepository.findById(reviewId);
        if (review.isEmpty()){
            return false;
        }
        reviewRepository.delete(review.get());
        return true;
    }

    @Override
    public boolean updateReview(Review review, Long reviewId) {
        Optional<Review> reviewDb = reviewRepository.findById(reviewId);
        if (reviewDb.isEmpty()){
            return false;
        }
        Review reviewUpdate = reviewDb.get();
        reviewUpdate.setTitle(review.getTitle());
        reviewUpdate.setDescription(review.getTitle());
        reviewUpdate.setRating(review.getRating());
        reviewUpdate.setCompanyId(review.getCompanyId());
        reviewRepository.save(reviewUpdate);
        return true;
    }
}
