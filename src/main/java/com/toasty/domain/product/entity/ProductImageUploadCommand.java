package com.toasty.domain.product.entity;

import java.util.List;

public record ProductImageUploadCommand(Long sellerId, List<File> files) {

    public record File(String contentType, long contentLength) {}
}
