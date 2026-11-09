package jvam90.review_service.controllers;

import jakarta.websocket.server.PathParam;
import jvam90.review_service.models.Review;
import jvam90.review_service.services.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping()
    public ResponseEntity<List<Review>> getAll(@PathVariable Long companyId){
        return ResponseEntity.ok(reviewService.findAll(companyId));
    }

    @GetMapping("{reviewId}")
    public ResponseEntity<Review> findById(@PathVariable Long reviewId){
        return reviewService.findById(reviewId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("{reviewId}")
    public ResponseEntity<String> deleteReview(@PathVariable Long reviewId){
        if(this.reviewService.deleteReview(reviewId)){
            return ResponseEntity.ok("Review deleted successfully");
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<String> updateCompany(@RequestBody Review review, @PathVariable Long reviewId){
        if(this.reviewService.updateReview(review, reviewId)){
            return ResponseEntity.ok("Company updated successfully");
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping()
    public ResponseEntity<Review> addReview(@RequestBody Review review, @PathVariable Long companyId){
        Review reviewCreated = this.reviewService.addReview(review, companyId);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(reviewCreated.getId())
                .toUri();
        return ResponseEntity.created(location).body(reviewCreated);
    }
}
