package org.example.project.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class MonthlyStatsDto {
    private String name;
    private int count;
}
