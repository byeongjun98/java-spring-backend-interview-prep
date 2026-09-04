package com.example.designreco.web;

import org.springframework.stereotype.Component;

import com.example.designreco.domain.Asset;
import com.example.designreco.domain.AssetTranslation;
import com.example.designreco.dto.AssetResponse;
import com.example.designreco.repository.AssetTranslationRepository;

import reactor.core.publisher.Mono;

/**
 * Asset + 번역 조회를 AssetResponse로 조립. 컨트롤러 2곳(AssetController의 에셋 조회/추천,
 * UserController의 유저 기반 추천)에서 공용으로 씀 — R2DBC가 JOIN을 자동으로 안 해주는 대신,
 * "Asset 하나 + 그에 맞는 번역 하나"를 조립하는 이 반복 패턴을 여기 한 곳에 모아서
 * 컨트롤러마다 같은 로직을 복붙하지 않게 함.
 */
@Component
public class AssetResponseAssembler {

	private final AssetTranslationRepository translationRepository;

	public AssetResponseAssembler(AssetTranslationRepository translationRepository) {
		this.translationRepository = translationRepository;
	}

	public Mono<AssetResponse> toResponse(Asset asset, String locale) {
		return translationRepository.findByAssetIdAndLocale(asset.getId(), locale)
			// switchIfEmpty: 앞의 Mono가 빈 채로 끝나면(= 이 locale 번역이 없으면) 대체 Publisher로 교체.
			// 요청 locale 번역이 없으면 기본 locale(en)로 폴백.
			.switchIfEmpty(translationRepository.findByAssetIdAndLocale(asset.getId(),
					AcceptLanguageResolver.DEFAULT_LOCALE))
			// map: Mono<AssetTranslation> → Mono<AssetResponse>. DB I/O 없는 단순 값 변환이라
			// (다시 리포지토리를 호출하는 게 아니라 순수 조립만) flatMap이 아니라 map으로 충분.
			.map(translation -> toResponse(asset, translation));
	}

	private AssetResponse toResponse(Asset asset, AssetTranslation translation) {
		return new AssetResponse(asset.getId(), asset.getCategory(), translation.getLocale(),
				translation.getTitle(), translation.getDescription());
	}
}
