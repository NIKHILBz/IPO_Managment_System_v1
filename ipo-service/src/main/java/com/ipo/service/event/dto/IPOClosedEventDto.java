package com.ipo.service.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event payload when IPO is closed and ready for allotment
 * Published to ipo-closed topic
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IPOClosedEventDto {

    private Long ipoId;

    private String ipoNumber;

    private Long totalSharesOffered;

    private Long totalApplicationsReceived;

    private Long totalSharesRequested;

    private LocalDateTime closedAt;

    private String allotmentMethod;  // "FAIR_LOTTERY", "PRO_RATA", etc.
}
