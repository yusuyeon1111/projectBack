package org.example.project.post.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.post.dto.ApplyMemeberDto;
import org.example.project.post.dto.ApplyPositionRequestDto;
import org.example.project.post.dto.ApplyResponseDto;
import org.example.project.post.dto.PostAcceptDto;
import org.example.project.post.entity.Post;
import org.example.project.post.entity.PostPosition;
import org.example.project.post.entity.PostPositionMember;
import org.example.project.post.repositoty.PostPositionMemberRepository;
import org.example.project.post.repositoty.PostPositionRepository;
import org.example.project.post.repositoty.PostRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final PostPositionRepository postPositionRepository;
    private final PostPositionMemberRepository postPositionMemberRepository;

    // 포지션 id, username으로 신청 여부 판단
    public List<Long> getAppliedPositions(Long postId, String username) {
        List<PostPositionMember> appliedList = postPositionMemberRepository.findByMemberUsernameAndPostPosition_Post_Id(username, postId);
        return appliedList.stream()
                .map(ppm -> ppm.getPostPosition().getId())
                .collect(Collectors.toList());
    }

    // 포지션 신청
    @Transactional
    public ResponseEntity<String> applyPostPosition(ApplyPositionRequestDto dto) {
        PostPosition position = postPositionRepository.findById(dto.getPositionId())
                .orElseThrow(()-> new RuntimeException("포지션이 존재하지 않습니다."));
        if ("END".equals(position.getStatus())) {
            return ResponseEntity.badRequest().body("모집이 마감된 포지션입니다.");
        }
        Member member = memberRepository.findByUsername(dto.getUsername())
                .orElseThrow(()-> new RuntimeException("사용자가 존재하지 않습니다."));
        boolean alreadyApplied = postPositionMemberRepository.existsByPostPositionAndMember(position, member);
        if(alreadyApplied) {
            return ResponseEntity.badRequest().body("이미 신청한 포지션입니다.");
        }

        PostPositionMember postPositionMember = new PostPositionMember();
        postPositionMember.setPostPosition(position);
        postPositionMember.setMember(member);
        postPositionMember.setStatus("APPLY");
        postPositionMember.setAppliedAt(LocalDateTime.now());
        postPositionMemberRepository.save(postPositionMember);

        return ResponseEntity.ok("포지션 신청이 완료되었습니다.");
    }

    // 포지션 수락
    public List<ApplyMemeberDto> acceptPosition(ApplyPositionRequestDto dto) {
        PostPositionMember position = postPositionMemberRepository.findById(dto.getPostPositionId())
                .orElseThrow(()-> new RuntimeException("포지션이 존재하지 않습니다."));
        PostPosition postPosition = postPositionRepository.findById(dto.getPositionId())
                .orElseThrow(()-> new RuntimeException("포지션이 존재하지 않습니다."));

        position.setStatus("ACCEPT");
        postPositionRepository.save(postPosition);
        Post post = postRepository.findById(dto.getPostId())
                .orElseThrow(() -> new RuntimeException("모집글이 존재하지 않습니다."));
        int positionCount = postPosition.getCount();
        long temp = postPositionMemberRepository.findCountByPostPositionId(postPosition.getId());
        int memberPositionCount = (int)temp;
        if(positionCount == memberPositionCount) {
            postPosition.setStatus("END");
            postPositionRepository.save(postPosition);
        }
        long endCount = postPositionRepository.countByPostIdAndStatus(dto.getPostId(), "END");
        long totalCount = postPositionRepository.countByPostId(dto.getPostId());
        if(endCount == totalCount) {
            post.setStatus("END");
            postRepository.save(post);
        }
        position.setAcceptedAt(LocalDateTime.now());
        postPositionMemberRepository.save(position);

        List<ApplyMemeberDto> list = postPositionMemberRepository.findPostPositionMemberByPostId(dto.getPostId());
        return list;
    }

    // 포지션 거절
    public List<ApplyMemeberDto>  rejectPosition(ApplyPositionRequestDto dto) {
        PostPositionMember position = postPositionMemberRepository.findById(dto.getPostPositionId())
                .orElseThrow(()-> new RuntimeException("포지션이 존재하지 않습니다."));
        PostPosition postPosition = postPositionRepository.findById(dto.getPositionId())
                .orElseThrow(()-> new RuntimeException("포지션이 존재하지 않습니다."));

        position.setStatus("REJECT");
        position.setRejectedAt(LocalDateTime.now());
        postPositionMemberRepository.save(position);
        List<ApplyMemeberDto> list = postPositionMemberRepository.findPostPositionMemberByPostId(dto.getPostId());
        return list;
    }

    // username으로 신청한 포지션 리스트 조회
    public List<ApplyResponseDto> findApplyPost(String username) {
        List<ApplyResponseDto> apply = postPositionMemberRepository.findAllByMemberUsername(username);
        if (apply.isEmpty()) {
            return new ArrayList<>();
        }
        return apply;
    }

    // 참여중인 프로젝트 조회
    public List<PostAcceptDto> findAcceptPost(String username) {
        List<PostAcceptDto> accept = postPositionMemberRepository.findAcceptedPostsByUsername(username);
        if(accept.isEmpty()) {
            return new ArrayList<>();
        }
        return accept.stream().map( map->new PostAcceptDto(
                map.getPostId(),
                map.getPostTitle(),
                map.getAcceptedAt(),
                map.getStartDate(),
                map.getEndDate(),
                map.getPostStatus()
        )).collect(Collectors.toList());
    }

    // 포지션 조회
    public List<ApplyResponseDto> getApplyPositionList(Long postId) {
        return postPositionMemberRepository.findByPostId(postId).stream()
                .map(arr -> {
                    PostPositionMember ppm = (PostPositionMember) arr[0];
                    Member m = (Member) arr[1];
                    PostPosition pp = ppm.getPostPosition();
                    Post p = pp.getPost();
                    return new ApplyResponseDto(
                            ppm.getId(),
                            m.getId(),
                            m.getName(),
                            pp.getId(),
                            pp.getRole(),
                            pp.getStatus(),
                            ppm.getStatus(),
                            p.getId(),
                            p.getTitle(),
                            p.getCategory(),
                            ppm.getAppliedAt(),
                            ppm.getAcceptedAt(),
                            ppm.getRejectedAt()
                    );
                })
                .toList();
    }
}
