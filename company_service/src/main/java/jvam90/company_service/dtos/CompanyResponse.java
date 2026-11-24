package jvam90.company_service.dtos;

public record CompanyResponse(Long id, String name, Integer employees,
                              String description, Double averageRating) {
}
