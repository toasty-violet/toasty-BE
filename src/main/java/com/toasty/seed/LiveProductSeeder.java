package com.toasty.seed;

import com.toasty.domain.live.entity.Live;
import com.toasty.domain.live.entity.LiveStatus;
import com.toasty.domain.live.repository.LiveRepository;
import com.toasty.domain.product.entity.LiveProduct;
import com.toasty.domain.product.entity.Product;
import com.toasty.domain.product.entity.ProductCreateCommand;
import com.toasty.domain.product.entity.ProductImage;
import com.toasty.domain.product.repository.LiveProductRepository;
import com.toasty.domain.product.repository.ProductImageRepository;
import com.toasty.domain.product.repository.ProductRepository;
import com.toasty.domain.seller.entity.Seller;
import com.toasty.domain.seller.repository.SellerRepository;
import com.toasty.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 개발용 라이브 편성 상품을 만든다. {@link LiveSeeder}가 만든 목 라이브에 붙으므로 그 뒤에 돌아야 한다.
 *
 * <p>방송 중인 라이브에는 고정한 상품과 아직 고정하지 않은 상품을 섞어 둬, 시청자 화면에서 구매 버튼이 열린 상품과 잠긴 상품이 함께 보인다. 고정 순서를 노출 순서와
 * 어긋나게 둬서 "현재 고정 상품"이 목록 맨 위가 아닌 자리를 가리키는 것도 확인할 수 있다.
 *
 * <p>값은 실제 결제 모듈로 결제해 볼 수 있도록 100원부터 1원씩 올려 붙인다. 라이브마다 구간이 겹치지 않아 결제 내역의 금액만 보고 어느 상품인지 찾을 수 있다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LiveProductSeeder {

    private static final int PRODUCTS_PER_LIVE = 20;

    // 판매자 1번 라이브가 100~119원, 2번이 120~139원 …으로 라이브마다 20원씩 띄어 쓴다.
    private static final int BASE_PRICE = 100;

    private static final int MOCK_STOCK_QUANTITY = 100;

    // 방송 중 라이브에서 셀러가 고정한 순서. 앞에서부터 차례로 고정한 것으로 보고, 맨 뒤가 현재 고정 상품이 된다.
    private static final List<Integer> PIN_SEQUENCE = List.of(0, 1, 5, 3, 7, 4, 6, 2);

    private static final int PIN_INTERVAL_MINUTES = 5;

    // 고정해 둔 상품 중 한 자리는 재고를 비워, 구매 버튼이 열린 채로 품절인 화면을 볼 수 있게 한다.
    private static final int SOLD_OUT_DISPLAY_ORDER = 3;

    private static final List<String> CATALOG =
            List.of(
                    "소금버터롤", "통밀식빵", "크루아상", "생크림단팥빵", "치즈스콘", "밀크롤", "무화과캄파뉴", "바질치아바타", "쑥인절미빵",
                    "초코소보로", "먹물베이글", "버터프레첼", "감자고로케", "얼그레이마들렌", "옥수수식빵", "에그타르트", "앙버터스콘",
                    "호두파운드", "라이스브레드", "시나몬롤");

    // 씨앗값이 같으면 같은 그림이 와서, 목데이터를 다시 깔아도 화면이 그대로다.
    private static final String MOCK_IMAGE_URL_FORMAT =
            "https://picsum.photos/seed/toasty-live-product-%d-%d/400/400";

    private static final List<LiveStatus> UNFINISHED_STATUSES =
            List.of(LiveStatus.READY, LiveStatus.LIVE);

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final LiveRepository liveRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final LiveProductRepository liveProductRepository;

    /** 편성 상품이 이미 있는 라이브는 건너뛰므로, 서버를 다시 켜도 중복으로 쌓이지 않는다. */
    @Transactional
    public void seed() {
        int created = 0;
        for (int sellerIndex = 1; sellerIndex <= UserSeeder.sellerCount(); sellerIndex++) {
            created += seedLive(sellerIndex);
        }
        log.info("목 라이브 상품 시딩 완료 — 새로 만든 상품 {}건", created);
    }

    // 목 유저·목 라이브 시딩이 건너뛴 자리는 편성 상품도 만들지 않는다.
    private int seedLive(int sellerIndex) {
        Live live = findUnfinishedLive(sellerIndex).orElse(null);
        if (live == null || hasScheduledProduct(live.getId())) {
            return 0;
        }
        List<LiveProduct> scheduled = new ArrayList<>();
        for (int order = 0; order < PRODUCTS_PER_LIVE; order++) {
            scheduled.add(schedule(live, sellerIndex, order));
        }
        if (live.getStatus() == LiveStatus.LIVE) {
            pinAll(scheduled);
        }
        return PRODUCTS_PER_LIVE;
    }

    private boolean hasScheduledProduct(Long liveId) {
        return !liveProductRepository.findByLiveIdOrderByDisplayOrder(liveId).isEmpty();
    }

    // 상품은 방송이 끝날 때까지 라이브 판매로 남으므로 closeLiveSales를 태우지 않는다.
    private LiveProduct schedule(Live live, int sellerIndex, int order) {
        boolean soldOut = live.getStatus() == LiveStatus.LIVE && order == SOLD_OUT_DISPLAY_ORDER;
        String name = CATALOG.get(order % CATALOG.size());
        ProductCreateCommand command =
                new ProductCreateCommand(
                        name,
                        price(sellerIndex, order),
                        soldOut ? 0 : MOCK_STOCK_QUANTITY,
                        "%s 상세 설명입니다. 로컬 목데이터로 만들어졌습니다.".formatted(name),
                        null);
        Product product =
                productRepository.save(Product.createForLive(live.getSellerId(), command));
        productImageRepository.save(
                ProductImage.createMain(
                        product.getId(), MOCK_IMAGE_URL_FORMAT.formatted(sellerIndex, order)));
        return liveProductRepository.save(
                LiveProduct.schedule(live.getId(), product.getId(), order));
    }

    // 고정 시각을 5분씩 벌려, 가장 최근에 고정한 상품 하나가 현재 고정 상품으로 잡히게 한다.
    private void pinAll(List<LiveProduct> scheduled) {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < PIN_SEQUENCE.size(); i++) {
            long minutesAgo = (long) (PIN_SEQUENCE.size() - i) * PIN_INTERVAL_MINUTES;
            scheduled.get(PIN_SEQUENCE.get(i)).pin(now.minusMinutes(minutesAgo));
        }
    }

    private int price(int sellerIndex, int order) {
        return BASE_PRICE + (sellerIndex - 1) * PRODUCTS_PER_LIVE + order;
    }

    private Optional<Live> findUnfinishedLive(int sellerIndex) {
        return userRepository
                .findByKakaoId(UserSeeder.sellerKakaoId(sellerIndex))
                .flatMap(user -> sellerRepository.findByUserId(user.getId()))
                .map(Seller::getId)
                .flatMap(
                        sellerId ->
                                liveRepository
                                        .findBySellerIdAndStatusInOrderByScheduledAtAsc(
                                                sellerId, UNFINISHED_STATUSES)
                                        .stream()
                                        .findFirst());
    }
}
