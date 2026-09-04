package com.example.designreco.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * 브라우저에서 돌아가는 프론트엔드(project/design-reco-frontend, localhost:5500)가 이 백엔드
 * (localhost:8080)를 fetch로 호출하려면 CORS 허용이 필요함 — 포트가 다르면 브라우저가 "다른 origin"으로
 * 취급해서, 서버가 명시적으로 허용하지 않는 한 응답을 자바스크립트에서 읽지 못하게 막음.
 * POST /events처럼 Content-Type: application/json을 쓰는 요청은 브라우저가 먼저 OPTIONS로
 * "이 요청 보내도 되냐"를 물어보는 프리플라이트를 보내는데, Spring이 이 설정을 보고 자동으로 응답해줌.
 */
@Configuration
public class WebConfig implements WebFluxConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
			.allowedOrigins("http://localhost:5500", "http://127.0.0.1:5500")
			.allowedMethods("GET", "POST")
			.allowedHeaders("*");
	}
}
