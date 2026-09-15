package com.toasty.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** 요청 흐름과 떼어 뒤에서 돌릴 작업에 붙이는 @Async를 켠다. */
@Configuration
@EnableAsync
public class AsyncConfig {}
