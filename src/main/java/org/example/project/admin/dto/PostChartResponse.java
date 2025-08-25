package org.example.project.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostChartResponse {
    private String name;
    private String category;
    private int count;
}
