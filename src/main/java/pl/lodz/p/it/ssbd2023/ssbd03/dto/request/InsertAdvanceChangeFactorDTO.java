package pl.lodz.p.it.ssbd2023.ssbd03.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import pl.lodz.p.it.ssbd2023.ssbd03.dto.VersionDTO;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class InsertAdvanceChangeFactorDTO extends VersionDTO {
    @NotNull
    @DecimalMin(value = "0")
    @DecimalMax(value = "9")
    private BigDecimal advanceChangeFactor;

    public InsertAdvanceChangeFactorDTO(@NotNull Long version, @NotNull BigDecimal advanceChangeFactor) {
        super(version);
        this.advanceChangeFactor = advanceChangeFactor;
    }
}
