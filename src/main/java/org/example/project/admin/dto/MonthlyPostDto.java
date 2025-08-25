package org.example.project.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MonthlyPostDto {
    private int month;
    private String name;
    private int totalPositions;
    private int applyCount;
    private int acceptCount;
}
