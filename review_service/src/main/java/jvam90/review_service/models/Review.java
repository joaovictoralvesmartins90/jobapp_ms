package jvam90.review_service.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private double rating;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Long companyId;

    public Review() {
    }

    public Review(String title, String description, double rating) {
        this.title = title;
        this.description = description;
        this.rating = rating;
    }

    @PrePersist
     void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public Long getCompanyId() { return companyId; }
    public void setCompany(Long companyId) { this.companyId = companyId; }
}
