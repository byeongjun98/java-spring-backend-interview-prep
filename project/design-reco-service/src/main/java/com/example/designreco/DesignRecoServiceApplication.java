package com.example.designreco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan 묶음.
// 클래스패스에 spring-boot-starter-webflux가 있으면 자동으로 Netty 기반 서버로 뜸
// (starter-web을 썼다면 서블릿/톰캣으로 떴을 것 — MVC와 WebFlux는 스타터 선택만으로 갈림).
@SpringBootApplication
public class DesignRecoServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DesignRecoServiceApplication.class, args);
	}

}
