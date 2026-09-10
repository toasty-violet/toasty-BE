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
    List<Product> findBySellerIdAndSalesTypeInAndIdLessThanOrderByIdDesc(
            Long sellerId, Collection<SalesType> salesTypes, Long cursor, Pageable pageable);

    /** 상품탭 상태 칩에 붙는 건수. */
    @Query(
            "select p.salesType as salesType, count(p) as productCount from Product p"
                    + " where p.sellerId = :sellerId and p.salesType in :salesTypes"
                    + " group by p.salesType")
    List<SellerProductCount> countBySalesType(
            @Param("sellerId") Long sellerId,
            @Param("salesTypes") Collection<SalesType> salesTypes);
}
