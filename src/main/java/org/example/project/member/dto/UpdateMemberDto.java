package org.example.project.member.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UpdateMemberDto {

    private String username;

    private String password;

    private String nickname;

    private String email;

    private String name;

    private String preferredPosition;

    private List<String> preferredTechStacks;

    private String introduce;
}
