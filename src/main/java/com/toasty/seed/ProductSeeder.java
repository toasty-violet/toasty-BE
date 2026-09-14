package com.toasty.seed;

import com.toasty.domain.product.entity.Product;
import com.toasty.domain.product.entity.ProductCreateCommand;
import com.toasty.domain.product.entity.ProductImage;
import com.toasty.domain.product.repository.ProductImageRepository;
import com.toasty.domain.product.repository.ProductRepository;
import com.toasty.domain.seller.entity.Seller;
import com.toasty.domain.seller.repository.SellerRepository;
import com.toasty.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 개발용 목 상품을 만든다. {@link UserSeeder}가 만든 목 판매자에게 붙이므로 그 뒤에 돌아야 한다.
 *
 * <p>스토어마다 상품을 스토어 카드 미리보기 개수보다 많이 두고 마지막 한 개는 품절로 남겨, 목록이 판매중 상품만 최신순으로 자르는지 눈으로 볼 수 있게 한다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class ProductSeeder {

    // 목 판매자마다 시작 위치만 옮겨 가며 여기서 상품을 가져다 쓴다. 스토어끼리 품목이 겹쳐도 상관없다.
    private static final List<MockProduct> CATALOG =
            List.of(
                    new MockProduct("소금버터롤", 3500),
                    new MockProduct("통밀식빵", 6800),
                    new MockProduct("크루아상", 4200),
                    new MockProduct("생크림단팥빵", 3000),
                    new MockProduct("치즈스콘", 4500),
                    new MockProduct("밀크롤", 3800),
                    new MockProduct("무화과캄파뉴", 12000),
                    new MockProduct("바질치아바타", 5500),
                    new MockProduct("쑥인절미빵", 4800),
                    new MockProduct("초코소보로", 3200));

    private static final int PRODUCTS_PER_SELLER = 5;

    private static final int MOCK_STOCK_QUANTITY = 20;

    // 씨앗값이 같으면 같은 그림이 와서, 목데이터를 다시 깔아도 화면이 그대로다.
    private static final String MOCK_IMAGE_URL_FORMAT =
            "https://picsum.photos/seed/toasty-product-%d-%d/400/400";

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;

    /** 상품이 이미 있는 스토어는 건너뛰므로, 서버를 다시 켜도 중복으로 쌓이지 않는다. */
    @Transactional
    public void seed() {
        int created = 0;
        for (int sellerIndex = 1; sellerIndex <= UserSeeder.sellerCount(); sellerIndex++) {
            created += seedStore(sellerIndex);
        }
        log.info("목 상품 시딩 완료 — 새로 만든 상품 {}건", created);
    }

    // 목 유저 시딩이 상점명 중복으로 건너뛴 자리는 상품도 만들지 않는다.
    private int seedStore(int sellerIndex) {
        Long sellerId = findSellerId(sellerIndex).orElse(null);
        if (sellerId == null || productRepository.countBySellerId(sellerId) > 0) {
            return 0;
        }
        for (int order = 0; order < PRODUCTS_PER_SELLER; order++) {
            MockProduct mock = CATALOG.get((sellerIndex + order) % CATALOG.size());
            // 맨 나중에 만드는 한 개만 재고를 비운다. 최신순 맨 앞자리가 품절이라 걸러내기가 눈에 띈다
            boolean soldOut = order == PRODUCTS_PER_SELLER - 1;
            save(sellerId, sellerIndex, order, mock, soldOut);
        }
        return PRODUCTS_PER_SELLER;
    }

    // 상품은 라이브용으로 만들어져 방송이 끝나면 판매중으로 넘어간다. 목 상품도 같은 길을 태운다.
    // 재고를 비워 두고 넘기면 품절이 된다.
    private void save(
            Long sellerId, int sellerIndex, int order, MockProduct mock, boolean soldOut) {
        ProductCreateCommand command =
                new ProductCreateCommand(
                        mock.name(),
                        mock.price(),
                        soldOut ? 0 : MOCK_STOCK_QUANTITY,
                        "%s 상세 설명입니다. 로컬 목데이터로 만들어졌습니다.".formatted(mock.name()),
                        null);
        Product product = Product.createForLive(sellerId, command);
        product.closeLiveSales();
        productRepository.save(product);
        productImageRepository.save(
                ProductImage.createMain(
                        product.getId(), MOCK_IMAGE_URL_FORMAT.formatted(sellerIndex, order)));
    }

    private Optional<Long> findSellerId(int index) {
        return userRepository
                .findByKakaoId(UserSeeder.sellerKakaoId(index))
                .flatMap(user -> sellerRepository.findByUserId(user.getId()))
                .map(Seller::getId);
    }

    private record MockProduct(String name, int price) {}
}
