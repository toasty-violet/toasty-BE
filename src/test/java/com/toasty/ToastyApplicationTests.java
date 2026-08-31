package com.toasty;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 컨텍스트를 띄우는 것만으로 Flyway 마이그레이션 전체 적용과 ddl-auto=validate 검증이 함께 돈다.
// CI가 마이그레이션 오류를 잡는 유일한 경로이므로 지우지 않는다.
@SpringBootTest
class ToastyApplicationTests {

    @Test
    void 컨텍스트가_로딩된다() {}
}
