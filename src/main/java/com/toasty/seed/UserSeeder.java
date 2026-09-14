package com.toasty.seed;

import com.toasty.domain.customer.entity.Customer;
import com.toasty.domain.customer.entity.CustomerOnboardingCommand;
import com.toasty.domain.customer.repository.CustomerRepository;
import com.toasty.domain.seller.entity.Seller;
import com.toasty.domain.seller.entity.SellerOnboardingCommand;
import com.toasty.domain.seller.repository.SellerRepository;
import com.toasty.domain.user.entity.Role;
import com.toasty.domain.user.entity.User;
import com.toasty.domain.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 개발용 목 유저를 만든다.
 *
 * <p>온보딩을 마친 구매자·판매자와 온보딩 전 유저를 섞어 둔다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class UserSeeder {

    // 목 유저의 kakaoId는 모두 이 접두사로 시작해, 실제 유저와 구분되고 한 번에 지울 수 있다
    private static final String MOCK_KAKAO_ID_PREFIX = "mock_";

    // 휴대폰 번호는 하이픈 없이 11자리로 저장한다
    private static final String MOCK_PHONE_NUMBER_FORMAT = "0101000%04d";

    // point3 결제창을 거치지 않고 만드는 유저라, 계좌 등록을 마친 것처럼 보이도록 가짜 payerId를 박아둔다
    private static final String MOCK_PAYER_ID_FORMAT = "mock_payer_%04d";

    // 온보딩 전 유저. 역할이 비어 있어 구매자·판매자 어느 쪽 정보도 없다
    private static final int PENDING_ONBOARDING_COUNT = 3;

    private static final List<MockCustomer> CUSTOMERS =
            List.of(
                    new MockCustomer("토스티", "김민준"),
                    new MockCustomer("빵순이", "이서연"),
                    new MockCustomer("아침식빵", "박도윤"),
                    new MockCustomer("바삭한하루", "최지우"),
                    new MockCustomer("버터러버", "정하준"),
                    new MockCustomer("잼발라", "강서윤"),
                    new MockCustomer("크루아상", "조예준"),
                    new MockCustomer("우유한잔", "윤지호"),
                    new MockCustomer("딸기공주", "장수아"),
                    new MockCustomer("구움과자", "임건우"),
                    new MockCustomer("노릇노릇", "한다은"),
                    new MockCustomer("치즈덕후", "오시우"),
                    new MockCustomer("단팥맨", "서하윤"),
                    new MockCustomer("겉바속촉", "신유진"));

    private static final List<String> SELLER_SHOP_NAMES =
            List.of("토스티상회", "골목빵집", "새벽제빵소", "버터앤솔트", "동네베이커리", "구름빵공방", "따끈한오븐", "밀당제과");

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final SellerRepository sellerRepository;

    /** 목 유저가 이미 있으면 건너뛰므로, 서버를 다시 켜도 중복으로 쌓이지 않는다. */
    @Transactional
    public void seed() {
        int created = 0;
        for (int i = 0; i < CUSTOMERS.size(); i++) {
            created += seedCustomer(CUSTOMERS.get(i), i + 1) ? 1 : 0;
        }
        for (int i = 0; i < SELLER_SHOP_NAMES.size(); i++) {
            created += seedSeller(SELLER_SHOP_NAMES.get(i), i + 1) ? 1 : 0;
        }
        for (int i = 1; i <= PENDING_ONBOARDING_COUNT; i++) {
            created += seedPendingOnboarding(i) ? 1 : 0;
        }
        log.info("목 유저 시딩 완료 — 새로 만든 유저 {}명", created);
    }

    /** 다른 시더가 여기서 만든 목 구매자를 찾아 쓸 때 쓴다. */
    static String customerKakaoId(int index) {
        return MOCK_KAKAO_ID_PREFIX + "customer_" + index;
    }

    /** 다른 시더가 여기서 만든 목 판매자를 찾아 쓸 때 쓴다. */
    static String sellerKakaoId(int index) {
        return MOCK_KAKAO_ID_PREFIX + "seller_" + index;
    }

    // 온보딩을 마친 구매자를 만든다. 배송지는 만들지 않는다
    private boolean seedCustomer(MockCustomer mock, int index) {
        String kakaoId = customerKakaoId(index);
        if (exists(kakaoId) || nicknameTaken(kakaoId, mock.nickname())) {
            return false;
        }
        User user = save(kakaoId, Role.CUSTOMER);
        CustomerOnboardingCommand command =
                new CustomerOnboardingCommand(
                        user.getId(),
                        mock.name(),
                        mock.nickname(),
                        MOCK_PHONE_NUMBER_FORMAT.formatted(index),
                        null,
                        null);
        customerRepository.save(
                Customer.createForOnboarding(command, MOCK_PAYER_ID_FORMAT.formatted(index)));
        return true;
    }

    // 온보딩을 마친 판매자를 만든다
    private boolean seedSeller(String shopName, int index) {
        String kakaoId = sellerKakaoId(index);
        if (exists(kakaoId) || shopNameTaken(kakaoId, shopName)) {
            return false;
        }
        User user = save(kakaoId, Role.SELLER);
        SellerOnboardingCommand command =
                new SellerOnboardingCommand(
                        user.getId(),
                        shopName,
                        null,
                        null,
                        shopName + " 대표",
                        MOCK_PHONE_NUMBER_FORMAT.formatted(index),
                        null,
                        null,
                        null);
        sellerRepository.save(Seller.createForOnboarding(command));
        return true;
    }

    // 가입만 하고 온보딩을 마치지 않은 유저를 만든다
    private boolean seedPendingOnboarding(int index) {
        String kakaoId = MOCK_KAKAO_ID_PREFIX + "pending_" + index;
        if (exists(kakaoId)) {
            return false;
        }
        userRepository.save(User.createFromKakao(kakaoId));
        return true;
    }

    private boolean exists(String kakaoId) {
        return userRepository.findByKakaoId(kakaoId).isPresent();
    }

    // customers.nickname이 UNIQUE라 실제 구매자가 쓰는 닉네임과 겹치면 그 목 유저만 건너뛴다
    private boolean nicknameTaken(String kakaoId, String nickname) {
        if (customerRepository.existsByNickname(nickname)) {
            log.warn("닉네임 '{}'을(를) 쓰는 구매자가 이미 있어 {} 시딩을 건너뛴다", nickname, kakaoId);
            return true;
        }
        return false;
    }

    // sellers.shop_name이 UNIQUE라 실제 판매자가 쓰는 상점명과 겹치면 그 목 유저만 건너뛴다
    private boolean shopNameTaken(String kakaoId, String shopName) {
        if (sellerRepository.existsByShopName(shopName)) {
            log.warn("상점명 '{}'을(를) 쓰는 판매자가 이미 있어 {} 시딩을 건너뛴다", shopName, kakaoId);
            return true;
        }
        return false;
    }

    private User save(String kakaoId, Role role) {
        User user = User.createFromKakao(kakaoId);
        user.completeOnboarding(role);
        return userRepository.save(user);
    }

    private record MockCustomer(String nickname, String name) {}
}
