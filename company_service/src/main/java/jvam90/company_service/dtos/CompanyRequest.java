package jvam90.company_service.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank @Size(max = 255) String name,
        @PositiveOrZero Integer employees,
        @NotBlank @Size(max = 1000) String description
) {
}
