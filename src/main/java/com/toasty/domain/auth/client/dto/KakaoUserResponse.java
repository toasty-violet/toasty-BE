package com.toasty.domain.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 카카오 사용자 정보 조회(GET /v2/user/me) 응답. 지금은 식별자만 쓴다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserResponse(@JsonProperty("id") Long id) {}
