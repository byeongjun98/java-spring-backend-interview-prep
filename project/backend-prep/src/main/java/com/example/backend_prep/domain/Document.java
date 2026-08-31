package com.example.backend_prep.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Version;
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

    // 낙관적 락(optimistic lock)용 버전 컬럼. 이 엔티티를 저장(UPDATE)할 때마다 Hibernate가
    // "WHERE id=? AND version=현재값" 조건을 자동으로 붙이고, 성공하면 값을 1 증가시킴.
    // 두 사용자가 같은 문서를 동시에 읽어서 각자 수정 후 저장하면, 먼저 저장한 쪽은 성공하고
    // (version 증가), 나중에 저장하는 쪽은 자기가 든 version이 이미 낡아서 조건에 안 걸려
    // 0건 반영됨 -> Hibernate가 이걸 감지해 ObjectOptimisticLockingFailureException을 던짐.
    // "비관적 락(SELECT ... FOR UPDATE로 아예 잠가버림)"과 달리, 잠그지 않고 저장 시점에만
    // 충돌 여부를 확인하는 방식이라 "낙관적"이라 부름.
    @Version
    private Long version;

    public Document(String title, String content, Member owner) {
        this.title = title;
        this.content = content;
        this.owner = owner;
        this.createAt = LocalDateTime.now();
    }

    // content를 그냥 필드 그대로 노출하는 setter 대신, "내용을 바꾼다"는 의도가 드러나는
    // 이름의 메서드로 만듦 — 나중에 이 메서드 안에 검증 로직(예: 빈 값 금지) 같은 걸
    // 넣고 싶어져도 여기 한 곳만 고치면 됨.
    public void changeContent(String newContent) {
        this.content = newContent;
    }
}
