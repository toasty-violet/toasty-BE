package com.toasty.domain.live.repository;

import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveRepository extends JpaRepository<Live, Long> {

    Optional<Live> findByPublicId(String publicId);

    /** 방송 중인 라이브. 배치가 시청자 수를 갱신한다. */
    List<Live> findByStatus(LiveStatus status);

    /** 종료된 지 오래됐는데 채팅방이 남아 있는 라이브. 배치가 회수한다. */
    List<Live> findByStatusAndEndedAtBeforeAndIvsChatRoomArnIsNotNull(
            LiveStatus status, LocalDateTime endedBefore);

    List<Live> findBySellerIdAndStatusInOrderByScheduledAtAsc(
            Long sellerId, Collection<LiveStatus> statuses);

    int countBySellerIdAndStatus(Long sellerId, LiveStatus status);
}
