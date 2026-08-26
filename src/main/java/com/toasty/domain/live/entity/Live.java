package com.toasty.domain.live.entity;

import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.global.entity.BaseTimeEntity;
import com.toasty.global.exception.CustomException;
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

    // LIVE일 때만 sellerId가 들어간다. unique 제약이 셀러당 동시 LIVE 1개를 강제한다.
    @Column(name = "active_seller_id")
    private Long activeSellerId;

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

    public boolean isOwnedBy(Long sellerId) {
        return this.sellerId.equals(sellerId);
    }

    public boolean isEnded() {
        return status == LiveStatus.ENDED;
    }

    // activeSellerId의 unique 제약이 셀러당 동시 LIVE 1개를 막는다.
    public void startBroadcast() {
        if (isEnded()) {
            throw new CustomException(LiveErrorCode.LIVE_ALREADY_ENDED);
        }
        if (status == LiveStatus.LIVE) {
            return;
        }
        this.status = LiveStatus.LIVE;
        this.startedAt = LocalDateTime.now();
        this.activeSellerId = sellerId;
    }

    public void end() {
        if (isEnded()) {
            return;
        }
        this.status = LiveStatus.ENDED;
        this.endedAt = LocalDateTime.now();
        this.activeSellerId = null;
    }
}
