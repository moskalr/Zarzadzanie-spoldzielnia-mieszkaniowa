package pl.lodz.p.it.ssbd2023.ssbd03.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdvanceForMonthDTO {
    private BigDecimal heatingPlaceAdvanceValue;
    private BigDecimal heatingCommunalAreaAdvanceValue;
    private BigDecimal hotWaterAdvanceValue;
    private Integer month;
    private Integer year;

    public AdvanceForMonthDTO(BigDecimal heatingPlaceAdvanceValue, BigDecimal heatingCommunalAreaAdvanceValue, Integer month, Integer year) {
        this.heatingPlaceAdvanceValue = heatingPlaceAdvanceValue;
        this.heatingCommunalAreaAdvanceValue = heatingCommunalAreaAdvanceValue;
        this.month = month;
        this.year = year;
    }

    public AdvanceForMonthDTO(BigDecimal hotWaterAdvanceValue, Integer month, Integer year) {
        this.hotWaterAdvanceValue = hotWaterAdvanceValue;
        this.month = month;
        this.year = year;
    }
}
