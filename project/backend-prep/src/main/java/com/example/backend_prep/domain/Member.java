package com.example.backend_prep.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// @Entity: 이 클래스가 DB 테이블과 매핑되는 JPA 엔티티라는 표시.
// @Table(name=...): 매핑할 실제 테이블명 지정 (안 쓰면 클래스명을 그대로 테이블명으로 씀).
// @Getter, @NoArgsConstructor: Lombok 어노테이션 — 컴파일 시점에 getName()/getEmail() 같은
// getter들과 파라미터 없는 기본 생성자를 자동으로 만들어줌. 소스 코드엔 안 보이지만 실제로
// 존재함 (IDE에서 자동완성 눌러보면 뜸). JPA는 리플렉션으로 객체를 만들 때 기본 생성자가
// 반드시 필요해서 @NoArgsConstructor가 필수.
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor
public class Member {

    // @Id: 이 필드가 기본키(PK)라는 표시.
    // @GeneratedValue(strategy = IDENTITY): 값을 DB의 AUTO INCREMENT(identity)에 위임 —
    // 자바 코드에서 id를 직접 안 채워도 INSERT 시점에 DB가 알아서 채워줌.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // @Column: 이 필드가 매핑될 컬럼의 제약조건 지정. nullable=false는 NOT NULL,
    // length는 varchar 길이 — DB 스키마(schema.sql)의 제약과 반드시 일치시켜야 함
    // (안 맞으면 저장은 되다가 나중에 값이 길어졌을 때 DB 에러가 남).
    @Column(nullable = false, length = 20)
    private String name;

    @Column(length = 50)
    private String email;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createAt;

    @Column(name = "update_at")
    private LocalDateTime updateAt;

    // 이건 우리가 직접 만든 생성자 — id는 안 받음 (DB가 채워줄 거라서).
    // @NoArgsConstructor로 생긴 기본 생성자는 JPA 내부용, 실제 코드에서 new Member()로
    // 직접 쓸 일은 거의 없고 이 생성자를 씀.
    public Member(String name, String email) {
        this.name = name;
        this.email = email;
        this.createAt = LocalDateTime.now();
    }
}
