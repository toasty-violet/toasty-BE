package com.toasty.domain.product.repository;

import com.toasty.domain.product.entity.ProductImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByDisplayOrder(Long productId);

    /** 대표 이미지를 고를 수 있도록 노출 순서로 정렬해 준다. 상품마다 첫 번째가 대표다. */
    List<ProductImage> findByProductIdInOrderByDisplayOrder(Collection<Long> productIds);
}
