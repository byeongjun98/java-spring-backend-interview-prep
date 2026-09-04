package com.example.designreco.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * asset 1건당 locale별로 한 행. (asset_id, locale) 유니크 제약은 schema.sql에서 관리.
 * 다국어를 title_ko/title_en/title_ja처럼 컬럼으로 늘리지 않고 행으로 분리한 이유:
 * 지원 locale이 늘어나도(예: 프랑스어 추가) 스키마 마이그레이션 없이 행만 추가하면 됨.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table("asset_translation")
public class AssetTranslation {

	@Id
	private Long id;

	// asset을 향한 외래키. R2DBC 엔티티는 연관관계 매핑(@ManyToOne 같은)을 지원하지 않으므로
	// Asset 객체 참조가 아니라 그냥 Long id 값으로 들고 있음 — 실제 asset 정보가 필요하면
	// 호출하는 쪽(AssetResponseAssembler)에서 AssetRepository로 별도 조회해서 조립.
	private Long assetId;

	private String locale;

	private String title;

	private String description;
}
