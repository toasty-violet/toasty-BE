package com.toasty.domain.sample.entity;

/** 생성 유스케이스의 Service 입력값. Controller가 Request DTO를 이 타입으로 바꿔서 넘긴다. */
public record SampleCreateCommand(String title, String content) {}
