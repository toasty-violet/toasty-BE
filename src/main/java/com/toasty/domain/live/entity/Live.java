package com.toasty.domain.live.entity;

import com.toasty.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Stream Key 값은 민감정보이므로 필드로 두지 않는다. 발급 시 응답으로만 전달한다. */
@Entity
@Getter
@Table(name = "lives")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Live extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LiveStatus status;

    // 외부 공유용 식별자. id를 그대로 노출하지 않는다.
    @Column(name = "public_id", nullable = false, length = 36)
    private String publicId;

    @Column(name = "ivs_channel_arn", nullable = false, length = 200)
    private String ivsChannelArn;

    @Column(name = "playback_url", nullable = false, length = 500)
    private String playbackUrl;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    private Live(
            Long sellerId,
            String title,
            String description,
            String publicId,
            String ivsChannelArn,
            String playbackUrl) {
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.status = LiveStatus.READY;
        this.publicId = publicId;
        this.ivsChannelArn = ivsChannelArn;
        this.playbackUrl = playbackUrl;
    }

    public static Live create(LiveCreateCommand command, String ivsChannelArn, String playbackUrl) {
        return new Live(
                command.sellerId(),
                command.title(),
                command.description(),
                UUID.randomUUID().toString(),
                ivsChannelArn,
                playbackUrl);
    }
}
