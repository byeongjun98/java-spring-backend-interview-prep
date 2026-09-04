package com.example.designreco.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.example.designreco.domain.UserEvent;

import reactor.core.publisher.Flux;

public interface UserEventRepository extends ReactiveCrudRepository<UserEvent, Long> {

	// "findFirst10By...OrderBy...Desc" — Spring Data 메서드 이름 규칙:
	// findFirst10 = LIMIT 10, By UserId = where user_id = ?, OrderByCreatedAtDesc = order by created_at desc.
	// 최근 이력 기반 추천(UserController)에서 쓸 "이 유저의 최근 N건" — 개수는 요청마다 안 바꿔도 돼서 상수로 고정.
	Flux<UserEvent> findFirst10ByUserIdOrderByCreatedAtDesc(Long userId);
}
