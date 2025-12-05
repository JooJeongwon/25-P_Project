package com.hyodream.backend.order.service;

import com.hyodream.backend.payment.service.PaymentService;

import com.hyodream.backend.order.domain.Order;
import com.hyodream.backend.order.domain.OrderItem;
import com.hyodream.backend.order.domain.OrderStatus;
import com.hyodream.backend.order.dto.OrderItemResponseDto;
import com.hyodream.backend.order.dto.OrderRequestDto;
import com.hyodream.backend.order.dto.OrderResponseDto;
import com.hyodream.backend.order.repository.OrderRepository;
import com.hyodream.backend.product.domain.Product;
import com.hyodream.backend.product.repository.ProductRepository;
import com.hyodream.backend.product.service.ProductService;
import com.hyodream.backend.user.domain.User;
import com.hyodream.backend.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;
    private final ProductService productService;

    // 주문 생성
    @Transactional
    public Long order(String username, List<OrderRequestDto> itemDtos) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        List<OrderItem> orderItems = new ArrayList<>();
        int totalAmount = 0; // 총 결제 금액 계산용 변수

        for (OrderRequestDto dto : itemDtos) {
            Product product = productRepository.findById(dto.getProductId())
                    .orElseThrow(() -> new RuntimeException("상품 없음"));

            OrderItem orderItem = OrderItem.createOrderItem(product.getId(), product.getPrice(), dto.getCount());
            orderItems.add(orderItem);

            // 판매량 증가 호출
            productService.increaseTotalSales(product.getId(), dto.getCount());

            // 금액 누적 (가격 * 수량)
            totalAmount += (product.getPrice() * dto.getCount());
        }

        // 주문서 생성 및 저장
        Order order = Order.createOrder(user.getId(), orderItems);
        orderRepository.save(order);

        // PaymentService에게 결제 처리 위임
        paymentService.processPayment(order.getId(), totalAmount, "CARD");

        return order.getId();
    }

    // 내 주문 내역 조회
    @Transactional(readOnly = true)
    public List<OrderResponseDto> getMyOrders(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        List<Order> orders = orderRepository.findAllByUserIdOrderByOrderDateDesc(user.getId());

        List<OrderResponseDto> dtos = new ArrayList<>();

        for (Order order : orders) {
            List<OrderItemResponseDto> itemDtos = new ArrayList<>();

            for (OrderItem item : order.getOrderItems()) {
                String productName = productRepository.findById(item.getProductId())
                        .map(Product::getName)
                        .orElse("판매 중지된 상품");

                itemDtos.add(new OrderItemResponseDto(item, productName));
            }

            dtos.add(new OrderResponseDto(order, itemDtos));
        }

        return dtos; // 조회 기능 끝
    }

    // 주문 취소
    @Transactional
    public void cancelOrder(Long orderId, String username) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문이 존재하지 않습니다."));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        // 본인 확인
        if (!order.getUserId().equals(user.getId())) {
            throw new RuntimeException("주문자만 취소할 수 있습니다.");
        }

        // 이미 취소된 주문인지 확인
        if (order.getStatus() == OrderStatus.CANCEL) {
            throw new RuntimeException("이미 취소된 주문입니다.");
        }

        // 상태 변경 -> CANCEL
        order.setStatus(OrderStatus.CANCEL);

        // 주문했던 상품들의 판매량 원상복구 (감소)
        for (OrderItem item : order.getOrderItems()) {
            productService.decreaseTotalSales(item.getProductId(), item.getCount());
        }
    }
}