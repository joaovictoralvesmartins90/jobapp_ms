package jvam90.job_service.dtos;

public class CompanyDto {
    private String name;
    private Integer employees;
    private String description;

    public CompanyDto() {

    }

    public CompanyDto(String name, Integer employees, String description) {
        this.name = name;
        this.employees = employees;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getEmployees() {
        return employees;
    }

    public void setEmployees(Integer employees) {
        this.employees = employees;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
