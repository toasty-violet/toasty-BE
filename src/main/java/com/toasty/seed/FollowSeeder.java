package com.toasty.seed;

import com.toasty.domain.customer.entity.Customer;
import com.toasty.domain.customer.repository.CustomerRepository;
import com.toasty.domain.follow.entity.Follow;
import com.toasty.domain.follow.repository.FollowRepository;
import com.toasty.domain.seller.entity.Seller;
import com.toasty.domain.seller.repository.SellerRepository;
import com.toasty.domain.user.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 개발용 목 팔로우를 만든다. {@link UserSeeder}가 만든 목 유저끼리 이어 주므로 그 뒤에 돌아야 한다.
 *
 * <p>인기 스토어 Top3가 갈리도록 팔로워 수를 계단식으로 벌려 두고, 뒤쪽 판매자는 팔로워 없이 남겨 목록에 잡히지 않는 스토어도 볼 수 있게 한다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class FollowSeeder {

    // 앞의 숫자는 목 판매자 번호, 뒤의 목록은 그를 팔로우하는 목 구매자 번호다.
    // 1번 구매자(토스티)는 1·3번 스토어만 팔로우해, 그 유저로 로그인하면 Top3에 팔로우 여부가 섞여 나온다.
    private static final List<MockFollow> FOLLOWS =
            List.of(
                    new MockFollow(1, List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)),
                    new MockFollow(2, List.of(2, 3, 4, 5, 6, 7, 8, 9, 10)),
                    new MockFollow(3, List.of(1, 3, 5, 7, 9, 11, 13)),
                    new MockFollow(4, List.of(2, 4, 6, 8)),
                    new MockFollow(5, List.of(13, 14)));

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final SellerRepository sellerRepository;
    private final FollowRepository followRepository;

    /** 이미 있는 팔로우는 건너뛰므로, 서버를 다시 켜도 중복으로 쌓이지 않는다. */
    @Transactional
    public void seed() {
        Map<Integer, Long> customerIds = findCustomerIds();
        int created = 0;
        for (MockFollow mock : FOLLOWS) {
            Long sellerId = findSellerId(mock.sellerIndex()).orElse(null);
            if (sellerId == null) {
                continue;
            }
            for (int customerIndex : mock.customerIndexes()) {
                created += follow(customerIds.get(customerIndex), sellerId) ? 1 : 0;
            }
        }
        log.info("목 팔로우 시딩 완료 — 새로 만든 팔로우 {}건", created);
    }

    // 목 유저 시딩이 닉네임·상점명 중복으로 건너뛴 자리는 팔로우도 만들지 않는다.
    private boolean follow(Long customerId, Long sellerId) {
        if (customerId == null
                || followRepository.existsByCustomerIdAndSellerId(customerId, sellerId)) {
            return false;
        }
        followRepository.save(Follow.create(customerId, sellerId));
        return true;
    }

    // 같은 구매자가 여러 스토어에 나오므로 번호마다 한 번만 찾아 둔다.
    private Map<Integer, Long> findCustomerIds() {
        Map<Integer, Long> customerIds = new HashMap<>();
        FOLLOWS.stream()
                .flatMap(mock -> mock.customerIndexes().stream())
                .distinct()
                .forEach(
                        index -> findCustomerId(index).ifPresent(id -> customerIds.put(index, id)));
        return customerIds;
    }

    private Optional<Long> findCustomerId(int index) {
        return userRepository
                .findByKakaoId(UserSeeder.customerKakaoId(index))
                .flatMap(user -> customerRepository.findByUserId(user.getId()))
                .map(Customer::getId);
    }

    private Optional<Long> findSellerId(int index) {
        return userRepository
                .findByKakaoId(UserSeeder.sellerKakaoId(index))
                .flatMap(user -> sellerRepository.findByUserId(user.getId()))
                .map(Seller::getId);
    }

    private record MockFollow(int sellerIndex, List<Integer> customerIndexes) {}
}
