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

    /** 스토어 화면의 상품 그리드. 셀러 상품탭과 같은 커서 방식이다. */
    // 살 수 없는 상품은 화면에 표시할 자리가 없어 아예 담지 않는다.
    List<Product> findBySellerIdAndSalesTypeAndStockQuantityGreaterThanAndIdLessThanOrderByIdDesc(
            Long sellerId,
            SalesType salesType,
            int minStockQuantity,
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
}
