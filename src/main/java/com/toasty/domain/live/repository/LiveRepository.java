package com.toasty.domain.live.repository;

import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveRepository extends JpaRepository<Live, Long> {

    Optional<Live> findByPublicId(String publicId);

    /** 홈 화면 1순위. 방송 중인 라이브를 준다. */
    List<Live> findByStatus(LiveStatus status, Pageable pageable);

    /** 홈 화면 2순위. 방송 예정인 라이브를 준다. */
    // 시각이 지났는데 켜지 않은 라이브는 담지 않는다. 목록 맨 위를 차지하기 때문이다.
    List<Live> findByStatusAndScheduledAtGreaterThanEqual(
            LiveStatus status, LocalDateTime scheduledFrom, Pageable pageable);

    /** 아직 끝나지 않은 라이브. 배치가 시청자 수를 갱신하고, 송출이 시작됐으면 상태도 올린다. */
    List<Live> findByStatusIn(Collection<LiveStatus> statuses);

    /** 시청자 수만 바꾼다. 배치가 라이브마다 엔티티를 붙였다 떼지 않도록 UPDATE 한 번으로 끝낸다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Live l set l.viewerCount = :viewerCount where l.id = :liveId")
    void updateViewerCount(@Param("liveId") Long liveId, @Param("viewerCount") int viewerCount);

    /** 종료된 지 오래됐는데 채팅방이 남아 있는 라이브. 배치가 회수한다. */
    List<Live> findByStatusAndEndedAtBeforeAndIvsChatRoomArnIsNotNull(
            LiveStatus status, LocalDateTime endedBefore);

    List<Live> findBySellerIdAndStatusInOrderByScheduledAtAsc(
            Long sellerId, Collection<LiveStatus> statuses);

    int countBySellerIdAndStatus(Long sellerId, LiveStatus status);
}
