package com.toasty.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** 메인 클래스로 합치지 않는다. JPA를 띄우지 않는 슬라이스 테스트가 auditing 빈을 못 찾아 실패한다. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {}
