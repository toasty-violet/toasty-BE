package com.toasty.domain.live.repository;

import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveRepository extends JpaRepository<Live, Long> {

    Optional<Live> findByPublicId(String publicId);

    List<Live> findBySellerIdAndStatusInOrderByScheduledAtAsc(
            Long sellerId, Collection<LiveStatus> statuses);
}
