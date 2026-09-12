package com.toasty.domain.product.repository;

import com.toasty.domain.product.entity.Product;
import com.toasty.domain.product.entity.SalesType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 셀러 상품탭 목록. 최신순이라 id 내림차순이고, 커서보다 작은 id부터 읽는다. */
    // 검색하지 않을 때는 빈 문자열이 들어와 모든 상품명에 걸린다.
    List<Product> findBySellerIdAndSalesTypeInAndNameContainingAndIdLessThanOrderByIdDesc(
            Long sellerId,
            Collection<SalesType> salesTypes,
            String keyword,
            Long cursor,
            Pageable pageable);

    /** 상품탭 상태 칩에 붙는 건수. 검색 중이면 그 결과 안에서 센다. */
    @Query(
            "select p.salesType as salesType, count(p) as productCount from Product p"
                    + " where p.sellerId = :sellerId and p.salesType in :salesTypes"
                    + " and p.name like concat('%', :keyword, '%')"
                    + " group by p.salesType")
    List<SellerProductCount> countBySalesType(
            @Param("sellerId") Long sellerId,
            @Param("salesTypes") Collection<SalesType> salesTypes,
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
