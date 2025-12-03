package com.hyodream.backend.order.service;

import com.hyodream.backend.order.domain.Order;
import com.hyodream.backend.order.domain.OrderItem;
import com.hyodream.backend.order.domain.OrderStatus;
import com.hyodream.backend.order.dto.OrderItemResponseDto;
import com.hyodream.backend.order.dto.OrderRequestDto;
import com.hyodream.backend.order.dto.OrderResponseDto;
import com.hyodream.backend.order.repository.OrderRepository;
import com.hyodream.backend.product.domain.Product;
import com.hyodream.backend.product.repository.ProductRepository;
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

    // 1. 주문 생성
    @Transactional
    public Long order(String username, List<OrderRequestDto> itemDtos) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderRequestDto dto : itemDtos) {
            Product product = productRepository.findById(dto.getProductId())
                    .orElseThrow(() -> new RuntimeException("상품 없음"));

            // 주문 상품 생성
            OrderItem orderItem = OrderItem.createOrderItem(product.getId(), product.getPrice(), dto.getCount());
            orderItems.add(orderItem);
        }

        // 주문서 생성
        Order order = Order.createOrder(user.getId(), orderItems);
        orderRepository.save(order);

        return order.getId();
    }

    // 2. 내 주문 내역 조회
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

        return dtos; // 👈 질문하신 부분: 조회 기능은 여기서 끝납니다.
    } // 👈 이 괄호 밖으로 나가야 합니다!

    // 3. 주문 취소 (New! 여기에 추가)
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
    }
}