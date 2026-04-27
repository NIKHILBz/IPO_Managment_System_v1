package com.ipo.service.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentEventDto implements Serializable {

    private Long investmentId;
    private Long ipoId;
    private Long investorId;
    private String investorEmail;
    private Long sharesRequested;
    private BigDecimal amountInvested;
    private Long sharesAllotted;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
