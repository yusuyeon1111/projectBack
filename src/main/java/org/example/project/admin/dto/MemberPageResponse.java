package org.example.project.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MemberPageResponse {
    private List<MemberResponse> content;
    private int totalPages;
    private long totalElements;
    private int page;
}
