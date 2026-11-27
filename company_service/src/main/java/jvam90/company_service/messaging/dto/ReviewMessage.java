package jvam90.company_service.messaging.dto;

public record ReviewMessage(
        Long id,
        String title,
        String description,
        double rating,
        Long companyId) {
}
