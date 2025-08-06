package org.example.project.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.project.member.entity.Member;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberResponseDto {
    private String username;

    private String nickname;

    private String email;

    private String name;

    private String preferredPosition;

    private List<String> preferredTechStacks;

    private String introduce;

    public MemberResponseDto(Member member) {
        this.username = member.getUsername();
        this.nickname = member.getNickname();
        this.email = member.getEmail();
        this.name = member.getName();

        // profile 객체가 null이 아닐 경우만 세팅
        if (member.getMemberProfile() != null) {
            this.introduce = member.getMemberProfile().getIntroduce();
            this.preferredPosition = member.getMemberProfile().getPreferredPosition();
            this.preferredTechStacks = member.getMemberProfile().getPreferredTechStacks();
        }
    }
}
