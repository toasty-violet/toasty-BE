package com.toasty.domain.customer.repository;

import com.toasty.domain.customer.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {}
