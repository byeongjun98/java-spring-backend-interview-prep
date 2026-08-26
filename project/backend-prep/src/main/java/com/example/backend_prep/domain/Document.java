package com.example.backend_prep.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

// @Entity/@Table/@Id/@GeneratedValue/@Column/@Getter/@NoArgsConstructor 기본 설명은 Member.java 참고.
@Entity
@Table(name = "document")
@Getter
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 50)
    private String title;

    @Column(length = 4000)
    private String content;

    // @ManyToOne: "Document 여러 개가 Member 하나를 가리킨다"는 다대일 관계 매핑.
    // @JoinColumn(name="owner_id"): 이 관계를 저장할 실제 FK 컬럼명 지정
    // (document 테이블의 owner_id 컬럼에 member.id 값이 들어감).
    // LAZY: Document를 조회해도 owner는 즉시 안 가져오고 프록시(빈 껍데기)만 채워둠.
    // 실제로 owner.getName() 등을 호출하는 시점에 그제서야 SELECT가 나감.
    // 이 지연 로딩 때문에 목록을 반복문으로 돌리면서 owner를 매번 건드리면 N+1이 재현됨
    // (DocumentRepository의 findTop20ByOrderByIdAsc 참고). JOIN FETCH로 미리 같이 가져오면 해결됨.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private Member owner;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createAt;

    @Column(name = "update_at")
    private LocalDateTime updateAt;

    public Document(String title, String content, Member owner) {
        this.title = title;
        this.content = content;
        this.owner = owner;
        this.createAt = LocalDateTime.now();
    }
}
