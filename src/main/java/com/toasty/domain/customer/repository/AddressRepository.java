package com.toasty.domain.customer.repository;

import com.toasty.domain.customer.entity.Address;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

    Optional<Address> findByCustomerIdAndIsDefaultTrue(Long customerId);
}
