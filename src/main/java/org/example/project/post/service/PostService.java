package org.example.project.post.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.post.dto.*;
import org.example.project.post.entity.*;
import org.example.project.post.repositoty.PostLikeRepository;
import org.example.project.post.repositoty.PostPositionMemberRepository;
import org.example.project.post.repositoty.PostPositionRepository;
import org.example.project.post.repositoty.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class PostService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final PostPositionRepository postPositionRepository;
    private final PostPositionMemberRepository postPositionMemberRepository;
    private final PostLikeRepository postLikeRepository;

    // 게시글 작성
    @Transactional
    public Long createPost(PostRequestDto dto) {
        Post post = new Post();
        String nickname = memberRepository.findNicknameByUsername(dto.getAuthor())
                .orElseThrow(() -> new IllegalArgumentException("사용자 닉네임을 찾을 수 없습니다."));
        post.setNickname(nickname);
        post.setTitle(dto.getTitle());
        post.setContent(dto.getContent());
        post.setAuthor(dto.getAuthor());
        post.setProjectType(dto.getProjectType());
        post.setRegion(dto.getRegion());
        post.setStatus(dto.getStatus());
        post.setCreated(new Date());
        post.setCategory(dto.getCategory());
        post.setStartDate(dto.getStartDate());
        post.setEndDate(dto.getEndDate());
        post.setPostStatus("SCHEDULED");
        if(dto.getPositions() != null) {
            for(PostRequestDto.PostPositionDto positionDto : dto.getPositions()) {
                PostPosition position = new PostPosition();
                position.setPost(post);
                position.setRole(positionDto.getRole());
                position.setCount(positionDto.getCount());
                position.setStatus(positionDto.getStatus());
                post.getPositions().add(position);
            }
        }
        if(dto.getTechStacks() != null) {
            for(String techStack : dto.getTechStacks()) {
                PostStack postStack = new PostStack();
                postStack.setPost(post);
                postStack.setTechStack(techStack);
                post.getTechStacks().add(postStack);
            }
        }
        Post savedPost = postRepository.save(post);
        return savedPost.getId();
    }

    // 게시글 상세보기
    @Transactional
    public PostResponseDto selectViewByIdWithUsername(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));
        post.setViewCount(post.getViewCount() + 1);
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        boolean likedByUser = postLikeRepository.findByPostIdAndMemberId(postId, member.getId()).isPresent();
        return new PostResponseDto(post, likedByUser);
    }

    // 게시글 상세보기 로그인 x
    @Transactional
    public PostResponseDto selectViewById(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));
        post.setViewCount(post.getViewCount() + 1);
        return new PostResponseDto(post);
    }

    // 게시글 수정
    @Transactional
    public PostResponseDto updatePost(@Valid PostRequestDto dto) {
        Post post = postRepository.findById(dto.getId())
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        if (dto.getContent() != null) {
            post.setContent(dto.getContent());
        }
        if (dto.getTitle() != null) {
            post.setTitle(dto.getTitle());
        }
        if (dto.getProjectType() != null) {
            post.setProjectType(dto.getProjectType());
        }
        if (dto.getRegion() != null) {
            post.setRegion(dto.getRegion());
        }
        if (dto.getStartDate() != null) {
            post.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            post.setEndDate(dto.getEndDate());
        }
        post.setUpdated(new Date());
        // 기술 스택 업데이트
        if (dto.getTechStacks() != null) {
            post.getTechStacks().clear(); // 기존 데이터 삭제
            for (String techStack : dto.getTechStacks()) {
                PostStack postStack = new PostStack();
                postStack.setPost(post);
                postStack.setTechStack(techStack);
                post.getTechStacks().add(postStack); // 리스트에 추가
            }
        }

        // 모집 포지션 업데이트
        if (dto.getPositions() != null) {
            post.getPositions().clear(); // 기존 데이터 삭제
            for (PostRequestDto.PostPositionDto positionDto : dto.getPositions()) {
                PostPosition position = new PostPosition();
                position.setPost(post);
                position.setRole(positionDto.getRole());
                post.getPositions().add(position); // 리스트에 추가
            }
        }

        return new PostResponseDto(post);
    }

    // 게시글 삭제
    public void deletePost(Long postId) {
        if(!postRepository.existsById(postId)) {
            throw new EntityNotFoundException("게시글이 존재하지 않습니다.");
        }
        postRepository.deleteById(postId);
    }

    // 게시글 전체 리스트 조회
    public Page<PostResponseDto> getPostList(PostRequestDto dto) {
        Sort sort = "popular".equalsIgnoreCase(dto.getSortType())
                ? Sort.by(Sort.Direction.DESC, "likeCount")
                : Sort.by(Sort.Direction.DESC, "updated", "created");

        Pageable pageable = PageRequest.of(dto.getPage(), dto.getSize(), sort);

        Page<Post> postPage = postRepository.findPostsWithFilter(
                dto.getCategory(),
                dto.getStatus(),
                dto.getPostStatus(),
                dto.getProjectType(),
                dto.getRegion(),
                dto.getPosition(),
                dto.getKeyword(),
                dto.getStackKeyword(),
                pageable
        );

        return postPage.map(PostResponseDto::new);
    }

    public List<PostResponseDto> findMyPosts(String username) {
        List<Post> post = postRepository.findByAuthor(username);
        if(post.isEmpty()) {
            return new ArrayList<>();
        }
        return post.stream().map(PostResponseDto::new).collect(Collectors.toList());
    }


    public ResponseEntity<String> likePost(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(()-> new RuntimeException("게시글을 찾을 수 없습니다."));
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(()-> new RuntimeException("사용자를 찾을 수 없습니다."));
        boolean alreadyLiked = post.getLikes().stream()
                .anyMatch(like -> like.getMember().getId().equals(member.getId()));
        if (alreadyLiked) {
            return ResponseEntity.badRequest().body("이미 좋아요한 게시글입니다.");
        }

        PostLike postLike = new PostLike();
        postLike.setPost(post);
        postLike.setMember(member);
        post.getLikes().add(postLike);
        post.setLikeCount(post.getLikeCount() + 1);
        postLikeRepository.save(postLike);

        return ResponseEntity.ok("좋아요 완료");
    }


    public List<PostResponseDto> findLikePost(String username) {
        List<PostResponseDto> like = postLikeRepository.findAllByUsername(username);
        return like;
    }

    @Transactional
    public ResponseEntity<String> unLikePost(Long id, String username) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(()-> new RuntimeException("사용자를 찾을 수 없습니다."));
        Post post = postRepository.findById(id)
                        .orElseThrow(()-> new RuntimeException("게시글을 찾을 수 없습니다."));
        post.setLikeCount(post.getLikeCount() - 1);
        postLikeRepository.deleteByPostIdAndMemberId(id, member.getId());

        return ResponseEntity.ok("좋아요 취소 완료");
    }


}
