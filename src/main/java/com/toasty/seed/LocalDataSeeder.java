package com.toasty.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 로컬 개발용 목데이터를 채운다. local 프로파일로 실행할 때만 동작한다. 도메인이 늘어나면 여기에 시더를 추가한다. */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataSeeder implements ApplicationRunner {

    private final UserSeeder userSeeder;
    private final FollowSeeder followSeeder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("로컬 목데이터 시딩 시작");
        // 시더의 트랜잭션 경계 밖이라, 시딩만 롤백되고 기동은 이어진다
        try {
            // 팔로우는 목 유저를 이어 주므로 유저 시딩이 끝난 뒤에 돈다
            userSeeder.seed();
            followSeeder.seed();
        } catch (Exception e) {
            log.warn("로컬 목데이터 시딩 실패 — 목데이터 없이 기동을 계속한다", e);
        }
    }
}
