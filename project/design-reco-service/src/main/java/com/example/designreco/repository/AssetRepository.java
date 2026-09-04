package com.example.designreco.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.example.designreco.domain.Asset;

// ReactiveCrudRepository<Asset, Long> 만 상속하면 findAll()/findById()/save()/findAllById() 등
// 기본 CRUD 메서드를 Spring Data가 구현체 없이 자동 생성해줌 — 전부 Mono/Flux를 반환하는 논블로킹 버전.
// 여기서 추가로 정의한 메서드가 없는 건, 지금까지 필요한 조회가 findAll/findById/findAllById로
// 다 커버돼서 커스텀 쿼리 메서드를 만들 이유가 없었기 때문.
public interface AssetRepository extends ReactiveCrudRepository<Asset, Long> {
}
