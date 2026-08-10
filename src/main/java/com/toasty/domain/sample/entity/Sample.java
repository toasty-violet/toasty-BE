package com.toasty.domain.sample.entity;

import com.toasty.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 도메인 구조 예시용 Entity. */
@Entity
@Getter
@Table(name = "samples")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sample extends BaseTimeEntity {

    // 필드를 바꿀 때는 Flyway 마이그레이션을 같이 추가한다. ddl-auto가 validate라 어긋나면 기동이 실패한다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 1000)
    private String content;

    private Sample(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public static Sample create(SampleCreateCommand command) {
        return new Sample(command.title(), command.content());
    }
}
