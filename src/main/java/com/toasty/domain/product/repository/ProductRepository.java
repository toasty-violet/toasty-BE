package com.toasty.domain.product.repository;

import com.toasty.domain.product.entity.Product;
import com.toasty.domain.product.entity.SalesType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 셀러 상품탭의 전체 칩. 다 팔린 상품만 빼고 최신순으로 준다. */
    // in으로 두 상태를 묶으면 상태가 앞에 선 인덱스를 골라 정렬이 따로 붙는다. 제외 조건이라야 id 인덱스를 탄다.
    // 검색하지 않을 때는 빈 문자열이 들어와 모든 상품명에 걸린다.
    List<Product> findBySellerIdAndSalesTypeNotAndNameContainingAndIdLessThanOrderByIdDesc(
            Long sellerId,
            SalesType excludedSalesType,
            String keyword,
            Long cursor,
            Pageable pageable);

    /** 셀러 상품탭의 상태 칩 하나. */
    List<Product> findBySellerIdAndSalesTypeAndNameContainingAndIdLessThanOrderByIdDesc(
            Long sellerId, SalesType salesType, String keyword, Long cursor, Pageable pageable);

    /** 스토어 화면의 상품 그리드. 셀러 상품탭과 같은 커서 방식이다. */
    // 판매중은 재고가 남아 있는 상태다. 다 팔리면 SOLD_OUT으로 넘어가므로 재고를 따로 보지 않는다.
    List<Product> findBySellerIdAndSalesTypeAndIdLessThanOrderByIdDesc(
            Long sellerId, SalesType salesType, Long cursor, Pageable pageable);

    /** 구매자가 여는 상품 상세. 스토어 목록과 같은 조건이라 목록에 없는 상품은 열리지 않는다. */
    Optional<Product> findByIdAndSalesType(Long id, SalesType salesType);

    /** 판매자가 등록해 둔 상품 수. 품절도 함께 센다. */
    long countBySellerId(Long sellerId);

    /** 상품탭 상태 칩에 붙는 건수. 검색 중이면 그 결과 안에서 센다. */
    @Query(
            "select p.salesType as salesType, count(p) as productCount from Product p"
                    + " where p.sellerId = :sellerId and p.salesType <> :excludedSalesType"
                    + " and p.name like concat('%', :keyword, '%')"
                    + " group by p.salesType")
    List<SellerProductCount> countBySalesType(
            @Param("sellerId") Long sellerId,
            @Param("excludedSalesType") SalesType excludedSalesType,
            @Param("keyword") String keyword);

    /** 스토어 카드에 붙는 상품 수. 스토어 화면 그리드에 실제로 뜨는 상품만 센다. */
    @Query(
            "select p.sellerId as sellerId, count(p) as productCount from Product p"
                    + " where p.sellerId in :sellerIds and p.salesType = :salesType"
                    + " group by p.sellerId")
    List<StoreProductCount> countBySellerIdInAndSalesType(
            @Param("sellerIds") Collection<Long> sellerIds,
            @Param("salesType") SalesType salesType);
}
