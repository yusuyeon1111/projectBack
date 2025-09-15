package org.example.project.member.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "member_profile")
public class MemberProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id")
    private Long id;

    @Column(name = "preferred_position")
    private String preferredPosition;

    @Column(name = "introduce")
    private String introduce;

    @Column(name = "imgUrl")
    private String imgUrl;

    @ElementCollection
    @CollectionTable(name = "member_stack", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "tech_stack")
    private List<String> preferredTechStacks = new ArrayList<>();
}

