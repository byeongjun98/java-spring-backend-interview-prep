package com.example.backend_prep.repository;

import com.example.backend_prep.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

// 커스텀 메서드 없이 JpaRepository의 기본 CRUD(save/findById/count 등)만 그대로 씀 —
// DummyDataInitializer의 count()/save()/getReferenceById()가 전부 여기서 옴.
// interface가 실제로 어떻게 동작하는지는 DocumentRepository.java 주석 참고.
public interface MemberRepository extends JpaRepository<Member, Integer> {
}
