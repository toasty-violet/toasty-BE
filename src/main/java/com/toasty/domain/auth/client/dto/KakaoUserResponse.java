package com.toasty.domain.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 카카오 사용자 정보 조회 응답
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserResponse(@JsonProperty("id") Long id) {}
