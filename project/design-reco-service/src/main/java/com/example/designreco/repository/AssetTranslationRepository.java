package com.example.designreco.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.example.designreco.domain.AssetTranslation;

import reactor.core.publisher.Mono;

public interface AssetTranslationRepository extends ReactiveCrudRepository<AssetTranslation, Long> {

	// Spring Data가 메서드 이름을 파싱해서 자동으로 SQL을 만들어줌
	// (findBy + AssetId + And + Locale → "where asset_id = ? and locale = ?").
	// 결과가 0건 또는 1건이라 Mono(0~1개 원소) — 여러 건이면 Flux를 썼어야 함.
	Mono<AssetTranslation> findByAssetIdAndLocale(Long assetId, String locale);
}
