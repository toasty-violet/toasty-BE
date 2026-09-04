package com.toasty.domain.customer.service;

import com.toasty.domain.customer.entity.Address;
import com.toasty.domain.customer.entity.Customer;
import com.toasty.domain.customer.entity.CustomerOnboardingCommand;
import com.toasty.domain.customer.repository.AddressRepository;
import com.toasty.domain.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;

    /** 온보딩 제출로 구매자 정보와 기본 배송지를 만든다. 유저의 역할 확정과 같은 트랜잭션에서 일어난다. */
    @Transactional
    public Customer createForOnboarding(CustomerOnboardingCommand command) {
        Customer customer = customerRepository.save(Customer.createForOnboarding(command));
        addressRepository.save(Address.createDefault(customer.getId(), command.address()));
        return customer;
    }
}
