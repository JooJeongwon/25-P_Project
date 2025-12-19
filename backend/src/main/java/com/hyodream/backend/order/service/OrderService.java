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
import com.hyodream.backend.user.service.UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserService userService;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;
    private final ProductService productService;

    // 주문 생성
    @Transactional
    public Long order(List<OrderRequestDto> itemDtos) {
        User user = userService.getCurrentUser();

        // 1. 요청된 상품 ID 수집 및 일괄 조회 (N+1 방지)
        List<Long> productIds = itemDtos.stream()
                .map(OrderRequestDto::getProductId)
                .toList();

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<OrderItem> orderItems = new ArrayList<>();
        int totalAmount = 0; // 총 결제 금액 계산용 변수

        for (OrderRequestDto dto : itemDtos) {
            Product product = productMap.get(dto.getProductId());
            
            if (product == null) {
                throw new RuntimeException("상품 없음 (ID: " + dto.getProductId() + ")");
            }

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
    public List<OrderResponseDto> getMyOrders() {
        User user = userService.getCurrentUser();

        List<Order> orders = orderRepository.findAllByUserIdOrderByOrderDateDesc(user.getId());

        // 1. 모든 주문 내역에서 Product ID 수집 (N+1 방지)
        Set<Long> productIds = orders.stream()
                .flatMap(o -> o.getOrderItems().stream())
                .map(OrderItem::getProductId)
                .collect(Collectors.toSet());

        // 2. 상품 정보 일괄 조회 및 Map 변환 (ID -> Name)
        Map<Long, String> productNameMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));

        List<OrderResponseDto> dtos = new ArrayList<>();

        for (Order order : orders) {
            List<OrderItemResponseDto> itemDtos = new ArrayList<>();

            for (OrderItem item : order.getOrderItems()) {
                // Map에서 조회 (DB 쿼리 발생 X)
                String productName = productNameMap.getOrDefault(item.getProductId(), "판매 중지된 상품");
                itemDtos.add(new OrderItemResponseDto(item, productName));
            }

            dtos.add(new OrderResponseDto(order, itemDtos));
        }

        return dtos; // 조회 기능 끝
    }

    // 주문 취소
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문이 존재하지 않습니다."));

        User user = userService.getCurrentUser();

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

        // 결제 취소 처리
        paymentService.cancelPayment(orderId);
    }
}