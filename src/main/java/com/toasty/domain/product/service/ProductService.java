package com.toasty.domain.product.service;

import com.toasty.domain.product.controller.dto.response.LiveProductResponse;
import com.toasty.domain.product.controller.dto.response.LiveProductsResponse;
import com.toasty.domain.product.controller.dto.response.SellerProductCountsResponse;
import com.toasty.domain.product.controller.dto.response.SellerProductDetailResponse;
import com.toasty.domain.product.controller.dto.response.SellerProductResponse;
import com.toasty.domain.product.controller.dto.response.SellerProductsResponse;
import com.toasty.domain.product.controller.dto.response.StoreProductResponse;
import com.toasty.domain.product.controller.dto.response.StoreProductsResponse;
import com.toasty.domain.product.entity.LiveProduct;
import com.toasty.domain.product.entity.LiveProductPinCommand;
import com.toasty.domain.product.entity.LiveProductUpdateCommand;
import com.toasty.domain.product.entity.Product;
import com.toasty.domain.product.entity.ProductCreateCommand;
import com.toasty.domain.product.entity.ProductImage;
import com.toasty.domain.product.entity.ProductUpsertCommand;
import com.toasty.domain.product.entity.SalesType;
import com.toasty.domain.product.entity.SellerProductFilter;
import com.toasty.domain.product.entity.SellerProductPageCommand;
import com.toasty.domain.product.entity.SellerProductUpdateCommand;
import com.toasty.domain.product.entity.StoreProductPageCommand;
import com.toasty.domain.product.exception.ProductErrorCode;
import com.toasty.domain.product.repository.LiveProductCount;
import com.toasty.domain.product.repository.LiveProductRepository;
import com.toasty.domain.product.repository.ProductImageRepository;
import com.toasty.domain.product.repository.ProductRepository;
import com.toasty.domain.product.repository.SellerProductCount;
import com.toasty.global.config.S3Properties;
import com.toasty.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** 셀러가 라이브에서 판매할 상품을 등록하고 그 라이브에 편성한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    // 셀러 상품탭과 스토어 화면이 한 번에 당겨오는 개수. 둘 다 무한스크롤이다.
    private static final int PRODUCT_PAGE_SIZE = 20;

    // 첫 페이지는 커서가 없다. id는 양수라 최댓값을 넣으면 맨 앞부터 읽는다.
    private static final long FIRST_PAGE_CURSOR = Long.MAX_VALUE;

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final LiveProductRepository liveProductRepository;
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    /** 라이브를 저장하기 전에 사진을 영구 경로로 복사하고 그 키를 보낸 순서대로 돌려준다. */
    // 트랜잭션 밖에서 돌아야 한다. S3 왕복이 상품 수만큼 반복돼 DB 커넥션을 잡고 있으면 안 된다.
    // 원본은 지우지 않는다. pending 경로는 수명주기 규칙이 정리하므로, 지우다 실패해 사진이 사라지는 경우를 아예 만들지 않는다.
    public List<String> copyImagesToPermanent(Long sellerId, List<ProductCreateCommand> commands) {
        List<String> copied = new ArrayList<>();
        try {
            for (ProductCreateCommand command : commands) {
                copied.add(copyToPermanent(sellerId, command.imageObjectKey()));
            }
            return copied;
        } catch (RuntimeException e) {
            deleteImagesQuietly(copied);
            throw e;
        }
    }

    /** 라이브 저장이 실패했을 때 이미 복사해 둔 사진을 치운다. */
    // 지우기 실패가 원래 예외를 가리지 않게 한다. 남은 객체는 로그로 추적한다.
    public void deleteImagesQuietly(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            if (objectKey == null) {
                continue;
            }
            try {
                s3Client.deleteObject(
                        DeleteObjectRequest.builder()
                                .bucket(s3Properties.bucket())
                                .key(objectKey)
                                .build());
            } catch (SdkException e) {
                log.error("상품 사진 정리 실패. 버킷에 고아 객체가 남았다 - objectKey={}", objectKey, e);
            }
        }
    }

    /**
     * 라이브를 저장할 때 함께 넘어온 상품들을 만들고 그 라이브에 편성한다. S3를 건드리지 않으므로 사진은 호출 전에 {@link
     * #copyImagesToPermanent}로 옮겨 두고 그 키를 같은 순서로 넘겨야 한다.
     */
    @Transactional
    public List<LiveProductResponse> registerForLive(
            Long liveId,
            Long sellerId,
            List<ProductCreateCommand> commands,
            List<String> imageObjectKeys) {
        List<LiveProductResponse> responses = new ArrayList<>();
        for (int order = 0; order < commands.size(); order++) {
            responses.add(
                    register(
                            liveId,
                            sellerId,
                            commands.get(order),
                            imageObjectKeys.get(order),
                            order));
        }
        return responses;
    }

    /** 라이브를 수정하기 전에 이번에 새로 올라온 사진만 영구 경로로 복사한다. 사진을 바꾸지 않은 자리는 null로 둔다. */
    // copyImagesToPermanent와 같은 이유로 트랜잭션 밖에서 돌아야 한다.
    public List<String> copyNewImagesToPermanent(
            Long sellerId, List<ProductUpsertCommand> commands) {
        List<String> copied = new ArrayList<>();
        try {
            for (ProductUpsertCommand command : commands) {
                if (command.isNew() && command.imageObjectKey() == null) {
                    throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_REQUIRED);
                }
                copied.add(
                        command.imageObjectKey() == null
                                ? null
                                : copyToPermanent(sellerId, command.imageObjectKey()));
            }
            return copied;
        } catch (RuntimeException e) {
            deleteImagesQuietly(copied);
            throw e;
        }
    }

    /**
     * 라이브 수정 화면에서 넘어온 상품 배열로 편성을 통째로 바꾸고, 더 이상 쓰지 않는 사진의 objectKey를 돌려준다. S3를 건드리지 않으므로 사진은 호출 전에
     * {@link #copyNewImagesToPermanent}로 옮겨 두고 그 키를 같은 순서로 넘겨야 한다. 돌려받은 키는 커밋된 뒤에 지운다.
     */
    @Transactional
    public List<String> replaceForLive(
            Long liveId,
            Long sellerId,
            List<ProductUpsertCommand> commands,
            List<String> newImageObjectKeys) {
        requireNoDuplicatedProduct(commands);

        Map<Long, LiveProduct> scheduled =
                liveProductRepository.findByLiveIdOrderByDisplayOrder(liveId).stream()
                        .collect(Collectors.toMap(LiveProduct::getProductId, Function.identity()));

        List<String> obsoleteImageObjectKeys = new ArrayList<>();
        Set<Long> keptProductIds = new HashSet<>();
        for (int order = 0; order < commands.size(); order++) {
            ProductUpsertCommand command = commands.get(order);
            String imageObjectKey = newImageObjectKeys.get(order);
            if (command.isNew()) {
                register(liveId, sellerId, toCreateCommand(command), imageObjectKey, order);
                continue;
            }
            obsoleteImageObjectKeys.addAll(
                    modify(
                            scheduled.get(command.productId()),
                            sellerId,
                            command,
                            imageObjectKey,
                            order));
            keptProductIds.add(command.productId());
        }

        List<LiveProduct> dropped =
                scheduled.values().stream()
                        .filter(liveProduct -> !keptProductIds.contains(liveProduct.getProductId()))
                        .toList();
        obsoleteImageObjectKeys.addAll(unscheduleAll(liveId, dropped));
        return obsoleteImageObjectKeys;
    }

    /** 라이브에 편성된 상품을 노출 순서대로 돌려준다. 상품과 대표 이미지를 각각 한 번에 묶어 읽는다. */
    @Transactional(readOnly = true)
    public List<LiveProductResponse> findScheduledProducts(Long liveId) {
        return toResponses(liveProductRepository.findByLiveIdOrderByDisplayOrder(liveId));
    }

    /** 방송 화면의 전체 상품 시트를 채운다. */
    // 현재 고정 상품은 편성 목록에 이미 딸려 온 pinnedAt으로 고른다. 같은 테이블을 두 번 읽지 않는다.
    @Transactional(readOnly = true)
    public LiveProductsResponse findLiveProducts(Long liveId) {
        List<LiveProduct> scheduled = liveProductRepository.findByLiveIdOrderByDisplayOrder(liveId);
        return new LiveProductsResponse(currentPinnedProductId(scheduled), toResponses(scheduled));
    }

    // display_order 순으로 받아 상품마다 첫 번째를 대표로 쓴다.
    private Map<Long, String> findMainImageUrls(List<Long> productIds) {
        return productImageRepository.findByProductIdInOrderByDisplayOrder(productIds).stream()
                .collect(
                        Collectors.toMap(
                                ProductImage::getProductId,
                                ProductImage::getImageUrl,
                                (main, rest) -> main));
    }

    private Long currentPinnedProductId(List<LiveProduct> scheduled) {
        return scheduled.stream()
                .filter(liveProduct -> liveProduct.getPinnedAt() != null)
                .max(Comparator.comparing(LiveProduct::getPinnedAt))
                .map(LiveProduct::getProductId)
                .orElse(null);
    }

    private List<LiveProductResponse> toResponses(List<LiveProduct> scheduled) {
        if (scheduled.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = scheduled.stream().map(LiveProduct::getProductId).toList();

        Map<Long, Product> products =
                productRepository.findAllById(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, String> mainImageUrls = findMainImageUrls(productIds);

        return scheduled.stream()
                .map(
                        liveProduct ->
                                LiveProductResponse.of(
                                        products.get(liveProduct.getProductId()),
                                        liveProduct,
                                        mainImageUrls.get(liveProduct.getProductId())))
                .toList();
    }

    /** 셀러가 방송 중에 상품을 고정한다. 이미 고정됐던 상품이면 고정 시각만 새로 찍는다. */
    // 한 번 고정한 상품은 계속 구매할 수 있다. 다른 상품을 고정해도 되돌리지 않는다.
    @Transactional
    public void pinForLive(LiveProductPinCommand command) {
        LiveProduct liveProduct = requireScheduled(command.liveId(), command.productId());
        if (requireOwnedProduct(command.productId(), command.sellerId()).isSoldOut()) {
            throw new CustomException(ProductErrorCode.PRODUCT_OUT_OF_STOCK);
        }
        liveProduct.pin(LocalDateTime.now());
    }

    /** 셀러가 방송 중에 가격과 재고를 고친다. 상품 추가·삭제는 방송 중에 할 수 없다. */
    @Transactional
    public void changePriceAndStockDuringLive(LiveProductUpdateCommand command) {
        requireScheduled(command.liveId(), command.productId());
        requireOwnedProduct(command.productId(), command.sellerId())
                .changePriceAndStock(command.price(), command.stockQuantity());
    }

    // 그 라이브에 편성된 상품인지 본다. 남의 라이브 상품 번호로는 통과할 수 없다.
    private LiveProduct requireScheduled(Long liveId, Long productId) {
        return liveProductRepository
                .findByLiveIdAndProductId(liveId, productId)
                .orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_IN_LIVE));
    }

    /** 라이브별 편성 상품 수를 한 번에 센다. 편성이 없는 라이브는 결과에 담기지 않는다. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> countScheduledProducts(Collection<Long> liveIds) {
        if (liveIds.isEmpty()) {
            return Map.of();
        }
        return liveProductRepository.countByLiveIdIn(liveIds).stream()
                .collect(
                        Collectors.toMap(
                                LiveProductCount::getLiveId, LiveProductCount::getProductCount));
    }

    /** 셀러 상품탭 한 묶음을 채운다. */
    // 상품마다 사진을 읽지 않고 한 번에 모아 읽는다. 건수는 스크롤 중에 바뀌지 않아 첫 요청에서만 센다.
    @Transactional(readOnly = true)
    public SellerProductsResponse findSellerProducts(SellerProductPageCommand command) {
        String keyword = command.keyword() == null ? "" : command.keyword();
        CursorPage page = toCursorPage(readSellerProducts(command, keyword));
        return new SellerProductsResponse(
                command.cursor() == null ? countSellerProducts(command.sellerId(), keyword) : null,
                toCards(page.products(), SellerProductResponse::of),
                page.nextCursor(),
                page.hasNext());
    }

    private List<Product> readSellerProducts(SellerProductPageCommand command, String keyword) {
        if (command.filter().isAll()) {
            return productRepository
                    .findBySellerIdAndSalesTypeNotAndNameContainingAndIdLessThanOrderByIdDesc(
                            command.sellerId(),
                            SellerProductFilter.EXCLUDED,
                            keyword,
                            cursorOf(command.cursor()),
                            oneMoreThanPage());
        }
        return productRepository
                .findBySellerIdAndSalesTypeAndNameContainingAndIdLessThanOrderByIdDesc(
                        command.sellerId(),
                        command.filter().salesType(),
                        keyword,
                        cursorOf(command.cursor()),
                        oneMoreThanPage());
    }

    /** 스토어 화면의 상품 그리드를 채운다. 지금 살 수 있는 상품만 담는다. */
    @Transactional(readOnly = true)
    public StoreProductsResponse findStoreProducts(StoreProductPageCommand command) {
        CursorPage page =
                toCursorPage(
                        productRepository.findBySellerIdAndSalesTypeAndIdLessThanOrderByIdDesc(
                                command.sellerId(),
                                SalesType.GENERAL,
                                cursorOf(command.cursor()),
                                oneMoreThanPage()));
        return new StoreProductsResponse(
                toCards(page.products(), StoreProductResponse::of),
                page.nextCursor(),
                page.hasNext());
    }

    private record CursorPage(List<Product> products, Long nextCursor, boolean hasNext) {}

    // 다음이 있는지는 한 장을 더 읽어서 가린다. 넘친 한 장은 toCursorPage가 잘라낸다.
    private PageRequest oneMoreThanPage() {
        return PageRequest.of(0, PRODUCT_PAGE_SIZE + 1);
    }

    private CursorPage toCursorPage(List<Product> found) {
        boolean hasNext = found.size() > PRODUCT_PAGE_SIZE;
        List<Product> products = hasNext ? found.subList(0, PRODUCT_PAGE_SIZE) : found;
        return new CursorPage(
                products, hasNext ? products.get(products.size() - 1).getId() : null, hasNext);
    }

    private Long cursorOf(Long cursor) {
        return cursor == null ? FIRST_PAGE_CURSOR : cursor;
    }

    // 카드는 상품마다 사진을 읽지 않고 한 번에 모아 읽는다.
    private <T> List<T> toCards(List<Product> products, BiFunction<Product, String, T> toCard) {
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, String> mainImageUrls =
                findMainImageUrls(products.stream().map(Product::getId).toList());
        return products.stream()
                .map(product -> toCard.apply(product, mainImageUrls.get(product.getId())))
                .toList();
    }

    private SellerProductCountsResponse countSellerProducts(Long sellerId, String keyword) {
        Map<SalesType, Integer> counted =
                productRepository
                        .countBySalesType(sellerId, SellerProductFilter.EXCLUDED, keyword)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        SellerProductCount::getSalesType,
                                        SellerProductCount::getProductCount));
        int onSale = counted.getOrDefault(SalesType.GENERAL, 0);
        int scheduled = counted.getOrDefault(SalesType.LIVE, 0);
        return new SellerProductCountsResponse(onSale + scheduled, onSale, scheduled);
    }

    /** 셀러 상품 수정 화면을 채운다. */
    @Transactional(readOnly = true)
    public SellerProductDetailResponse findSellerProduct(Long productId, Long sellerId) {
        Product product = requireOwnProduct(productId, sellerId);
        List<SellerProductDetailResponse.Image> images =
                productImageRepository.findByProductIdOrderByDisplayOrder(productId).stream()
                        .map(
                                image ->
                                        toObjectKey(image.getImageUrl())
                                                .map(
                                                        key ->
                                                                new SellerProductDetailResponse
                                                                        .Image(
                                                                        key, image.getImageUrl()))
                                                .orElse(null))
                        .filter(Objects::nonNull)
                        .toList();
        return SellerProductDetailResponse.of(product, images);
    }

    /** 이 상품이 편성된 라이브. 상품탭이 방송 중인지 확인하는 데 쓴다. */
    // 남의 상품을 넘겨 편성을 엿보지 못하도록 소유부터 확인한다.
    @Transactional(readOnly = true)
    public List<Long> findScheduledLiveIds(Long productId, Long sellerId) {
        requireOwnProduct(productId, sellerId);
        return liveProductRepository.findLiveIdsByProductId(productId);
    }

    /** 넘긴 라이브 중 편성 상품이 하나뿐인 것이 있는지. 상품탭이 마지막 상품 삭제를 막는 데 쓴다. */
    @Transactional(readOnly = true)
    public boolean hasLiveWithSingleProduct(Collection<Long> liveIds) {
        return countScheduledProducts(liveIds).values().stream().anyMatch(count -> count <= 1);
    }

    /** 상품탭 수정 전에 이번에 새로 올린 사진만 영구 경로로 옮긴다. 그대로 두는 사진은 받은 키를 돌려준다. */
    // copyImagesToPermanent와 같은 이유로 트랜잭션 밖에서 돌아야 한다.
    public List<String> copySellerImagesToPermanent(Long sellerId, List<String> objectKeys) {
        List<String> copied = new ArrayList<>();
        try {
            for (String objectKey : objectKeys) {
                copied.add(isPending(objectKey) ? copyToPermanent(sellerId, objectKey) : objectKey);
            }
            return copied;
        } catch (RuntimeException e) {
            deleteCopiedImagesQuietly(objectKeys, copied);
            throw e;
        }
    }

    /** 방금 복사한 사진만 지운다. 그대로 둔 사진은 아직 DB가 참조하고 있어 건드리지 않는다. */
    public void deleteCopiedImagesQuietly(List<String> objectKeys, List<String> permanentKeys) {
        List<String> copied = new ArrayList<>();
        for (int i = 0; i < permanentKeys.size(); i++) {
            if (isPending(objectKeys.get(i))) {
                copied.add(permanentKeys.get(i));
            }
        }
        deleteImagesQuietly(copied);
    }

    /** 상품탭에서 상품 하나를 고치고, 더 이상 쓰지 않는 사진의 objectKey를 돌려준다. 돌려받은 키는 커밋된 뒤에 지운다. */
    @Transactional
    public List<String> updateSellerProduct(
            SellerProductUpdateCommand command, List<String> permanentKeys) {
        Product product = requireOwnProduct(command.productId(), command.sellerId());
        product.update(
                command.name(), command.price(), command.stockQuantity(), command.description());
        return replaceImages(product.getId(), command.imageObjectKeys(), permanentKeys);
    }

    /** 상품탭에서 상품 하나를 지우고, 지울 사진의 objectKey를 돌려준다. 돌려받은 키는 커밋된 뒤에 지운다. */
    // 편성 행을 먼저 지운다. 끝난 라이브의 편성이 남아 있어 그대로 두면 외래키에 걸린다.
    @Transactional
    public List<String> deleteSellerProduct(Long productId, Long sellerId) {
        Product product = requireOwnProduct(productId, sellerId);
        List<ProductImage> images =
                productImageRepository.findByProductIdOrderByDisplayOrder(productId);
        List<String> objectKeys =
                images.stream()
                        .map(image -> toObjectKey(image.getImageUrl()))
                        .flatMap(Optional::stream)
                        .toList();
        liveProductRepository.deleteByProductId(productId);
        productImageRepository.deleteAllInBatch(images);
        productRepository.delete(product);
        return objectKeys;
    }

    // 상품탭은 라이브 편성과 무관하게 자기 상품만 다룬다.
    private Product requireOwnProduct(Long productId, Long sellerId) {
        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow(() -> new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND));
        if (!product.getSellerId().equals(sellerId)) {
            throw new CustomException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    private boolean isPending(String objectKey) {
        return objectKey.startsWith(s3Properties.pendingPrefix());
    }

    // 사진은 최대 다섯 장이라 통째로 다시 깐다. 순서가 바뀐 자리를 따로 가려내지 않는다.
    private List<String> replaceImages(
            Long productId, List<String> objectKeys, List<String> permanentKeys) {
        List<ProductImage> existing =
                productImageRepository.findByProductIdOrderByDisplayOrder(productId);
        requireKeptImagesOwned(objectKeys, existingObjectKeys(existing));
        Set<String> kept = Set.copyOf(permanentKeys);
        List<String> obsolete =
                existing.stream()
                        .map(image -> toObjectKey(image.getImageUrl()))
                        .flatMap(Optional::stream)
                        .filter(objectKey -> !kept.contains(objectKey))
                        .toList();
        productImageRepository.deleteAllInBatch(existing);
        for (int order = 0; order < permanentKeys.size(); order++) {
            productImageRepository.save(
                    ProductImage.create(productId, toImageUrl(permanentKeys.get(order)), order));
        }
        return obsolete;
    }

    // 이번에 올린 사진이 아니면 원래 이 상품에 붙어 있던 것만 남길 수 있다.
    // 확정된 사진 경로는 복사를 거치지 않아 소유 검사도 지나가므로, 남의 사진 주소를 그대로 넣는 길을 여기서 막는다.
    private void requireKeptImagesOwned(List<String> objectKeys, Set<String> existingObjectKeys) {
        boolean borrowed =
                objectKeys.stream()
                        .anyMatch(
                                objectKey ->
                                        !isPending(objectKey)
                                                && !existingObjectKeys.contains(objectKey));
        if (borrowed) {
            throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_FORBIDDEN);
        }
    }

    private Set<String> existingObjectKeys(List<ProductImage> images) {
        return images.stream()
                .map(image -> toObjectKey(image.getImageUrl()))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
    }

    /** 라이브가 끝나면 편성 상품의 판매 방식을 정리한다. */
    @Transactional
    public void closeLiveSales(Long liveId) {
        List<LiveProduct> scheduled = liveProductRepository.findByLiveIdOrderByDisplayOrder(liveId);
        if (scheduled.isEmpty()) {
            return;
        }
        productRepository
                .findAllById(scheduled.stream().map(LiveProduct::getProductId).toList())
                .forEach(Product::closeLiveSales);
    }

    /** 라이브가 지워질 때 그 라이브의 편성과 상품을 정리하고, 더 이상 쓰지 않는 사진의 objectKey를 돌려준다. 돌려받은 키는 커밋된 뒤에 지운다. */
    @Transactional
    public List<String> removeAllForLive(Long liveId) {
        return unscheduleAll(liveId, liveProductRepository.findByLiveIdOrderByDisplayOrder(liveId));
    }

    // 편성에 남의 상품이 섞여 있으면 지우지도 고치지도 않는다.
    private Product requireOwnedProduct(Long productId, Long sellerId) {
        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow(
                                () -> new CustomException(ProductErrorCode.PRODUCT_NOT_IN_LIVE));
        if (!product.getSellerId().equals(sellerId)) {
            throw new CustomException(ProductErrorCode.PRODUCT_NOT_IN_LIVE);
        }
        return product;
    }

    // 같은 상품이 두 번 오면 뒤엣것이 앞엣것을 덮어써 편성이 조용히 줄고 순서도 밀린다.
    private void requireNoDuplicatedProduct(List<ProductUpsertCommand> commands) {
        List<Long> productIds =
                commands.stream()
                        .map(ProductUpsertCommand::productId)
                        .filter(Objects::nonNull)
                        .toList();
        if (productIds.size() != Set.copyOf(productIds).size()) {
            throw new CustomException(ProductErrorCode.PRODUCT_DUPLICATED);
        }
    }

    // 편성 여부와 셀러를 함께 본다. 남의 라이브 상품이나 편성되지 않은 상품 번호로는 통과할 수 없다.
    private List<String> modify(
            LiveProduct liveProduct,
            Long sellerId,
            ProductUpsertCommand command,
            String imageObjectKey,
            int displayOrder) {
        if (liveProduct == null) {
            throw new CustomException(ProductErrorCode.PRODUCT_NOT_IN_LIVE);
        }
        Product product = requireOwnedProduct(command.productId(), sellerId);
        product.update(
                command.name(), command.price(), command.stockQuantity(), command.description());
        liveProduct.changeDisplayOrder(displayOrder);
        if (imageObjectKey == null) {
            return List.of();
        }
        return replaceMainImage(product.getId(), imageObjectKey);
    }

    private List<String> replaceMainImage(Long productId, String imageObjectKey) {
        List<ProductImage> images =
                productImageRepository.findByProductIdOrderByDisplayOrder(productId);
        if (images.isEmpty()) {
            productImageRepository.save(
                    ProductImage.createMain(productId, toImageUrl(imageObjectKey)));
            return List.of();
        }
        ProductImage main = images.get(0);
        String replaced = main.getImageUrl();
        main.changeImageUrl(toImageUrl(imageObjectKey));
        return toObjectKey(replaced).map(List::of).orElseGet(List::of);
    }

    // 다른 라이브에도 편성된 상품은 편성만 푼다. 그 라이브에서 상품이 사라지면 안 된다.
    // 상품 수와 무관하게 쿼리가 일정하도록 묶고, 삭제 순서로 외래키를 직접 지킨다.
    private List<String> unscheduleAll(Long liveId, List<LiveProduct> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = targets.stream().map(LiveProduct::getProductId).toList();
        Set<Long> keptElsewhere =
                Set.copyOf(
                        liveProductRepository.findProductIdsScheduledInOtherLives(
                                productIds, liveId));
        List<Long> deletableProductIds =
                productIds.stream()
                        .filter(productId -> !keptElsewhere.contains(productId))
                        .toList();

        List<ProductImage> images =
                deletableProductIds.isEmpty()
                        ? List.of()
                        : productImageRepository.findByProductIdInOrderByDisplayOrder(
                                deletableProductIds);
        List<String> objectKeys =
                images.stream()
                        .map(image -> toObjectKey(image.getImageUrl()))
                        .flatMap(Optional::stream)
                        .toList();

        liveProductRepository.deleteAllInBatch(targets);
        productImageRepository.deleteAllInBatch(images);
        productRepository.deleteAllByIdInBatch(deletableProductIds);
        return objectKeys;
    }

    private ProductCreateCommand toCreateCommand(ProductUpsertCommand command) {
        return new ProductCreateCommand(
                command.name(),
                command.price(),
                command.stockQuantity(),
                command.description(),
                command.imageObjectKey());
    }

    private LiveProductResponse register(
            Long liveId,
            Long sellerId,
            ProductCreateCommand command,
            String imageObjectKey,
            int displayOrder) {
        Product product = productRepository.save(Product.createForLive(sellerId, command));
        ProductImage image =
                productImageRepository.save(
                        ProductImage.createMain(product.getId(), toImageUrl(imageObjectKey)));
        LiveProduct liveProduct =
                liveProductRepository.save(
                        LiveProduct.schedule(liveId, product.getId(), displayOrder));

        return LiveProductResponse.of(product, liveProduct, image.getImageUrl());
    }

    // 복사가 곧 검증이다. 업로드 주소만 받고 실제로 올리지 않았으면 원본이 없어 NoSuchKey가 난다.
    private String copyToPermanent(Long sellerId, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_REQUIRED);
        }
        requireOwnedBySeller(sellerId, objectKey);

        String destinationKey = toPermanentKey(objectKey);
        try {
            s3Client.copyObject(
                    CopyObjectRequest.builder()
                            .sourceBucket(s3Properties.bucket())
                            .sourceKey(objectKey)
                            .destinationBucket(s3Properties.bucket())
                            .destinationKey(destinationKey)
                            .build());
            return destinationKey;
        } catch (NoSuchKeyException e) {
            throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_NOT_UPLOADED, e);
        } catch (SdkException e) {
            log.error("상품 사진 복사 실패. objectKey={}", objectKey, e);
            throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_SAVE_FAILED, e);
        }
    }

    // 셀러·날짜·uuid를 그대로 두고 접두어만 바꾼다. 이미 pending 접두어로 시작하는지 검증한 뒤라 안전하다.
    private String toPermanentKey(String objectKey) {
        return s3Properties.imagePrefix()
                + objectKey.substring(s3Properties.pendingPrefix().length());
    }

    // 업로드 주소를 발급할 때 키에 넣은 셀러 번호로, 남의 사진을 자기 상품에 붙이는 것을 막는다.
    private void requireOwnedBySeller(Long sellerId, String objectKey) {
        if (!objectKey.startsWith(s3Properties.pendingPrefix() + sellerId + "/")) {
            throw new CustomException(ProductErrorCode.PRODUCT_IMAGE_FORBIDDEN);
        }
    }

    private String toImageUrl(String objectKey) {
        return s3Properties.publicBaseUrl() + "/" + objectKey;
    }

    // publicBaseUrl이 바뀐 뒤에 저장된 주소는 지울 키를 알 수 없어 그대로 남긴다. 사진이 사라지는 방향의 실패를 만들지 않는다.
    private Optional<String> toObjectKey(String imageUrl) {
        String prefix = s3Properties.publicBaseUrl() + "/";
        if (!imageUrl.startsWith(prefix)) {
            log.warn("사진 주소에서 objectKey를 얻지 못해 정리를 건너뛴다 - imageUrl={}", imageUrl);
            return Optional.empty();
        }
        return Optional.of(imageUrl.substring(prefix.length()));
    }
}
