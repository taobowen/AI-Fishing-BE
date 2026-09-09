package com.aifishing.fishingsession.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LocationBatchRequest(
        @NotEmpty
        @Size(max = 100)
        List<@Valid LocationPointRequest> points
) {
}
