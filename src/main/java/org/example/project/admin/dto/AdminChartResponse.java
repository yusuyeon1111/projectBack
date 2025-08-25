package org.example.project.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class AdminChartResponse {
    private List<MonthlyStatsDto> memberStatus;
    private List<MonthlyStatsDto> postStatus;
    private List<MonthlyStatsDto> postStsStatus;
}
