package com.toasty.domain.seller.repository;

import com.toasty.domain.seller.entity.Seller;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerRepository extends JpaRepository<Seller, Long> {

    Optional<Seller> findByUserId(Long userId);

    /** 보여줄 스토어를 고를 기준이 없는 화면을 채운다. 먼저 만들어진 순으로 준다. */
    List<Seller> findAllByWithdrawnAtIsNullOrderByIdAsc(Pageable pageable);

    /** 목록 화면이 거는 스토어. 탈퇴한 스토어는 자리에서 뺀다. */
    List<Seller> findByIdInAndWithdrawnAtIsNull(Collection<Long> sellerIds);

    boolean existsByBusinessNumber(String businessNumber);

    boolean existsByShopName(String shopName);

    boolean existsByShopNameAndIdNot(String shopName, Long id);
}
