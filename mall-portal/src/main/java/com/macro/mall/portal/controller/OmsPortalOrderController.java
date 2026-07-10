package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.constant.BehaviorEventType;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.component.BehaviorEventRecorder;
import com.macro.mall.portal.domain.ConfirmOrderResult;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.domain.OrderParam;
import com.macro.mall.portal.service.OmsPortalOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单管理Controller
 * Created by macro on 2018/8/30.
 */
@Controller
@Tag(name = "OmsPortalOrderController", description = "订单管理")
@RequestMapping("/order")
public class OmsPortalOrderController {
    @Autowired
    private OmsPortalOrderService portalOrderService;
    @Autowired
    private BehaviorEventRecorder behaviorEventRecorder;

    @Operation(summary = "根据购物车信息生成确认单信息")
    @RequestMapping(value = "/generateConfirmOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<ConfirmOrderResult> generateConfirmOrder(@RequestBody List<Long> cartIds) {
        ConfirmOrderResult confirmOrderResult = portalOrderService.generateConfirmOrder(cartIds);
        return CommonResult.success(confirmOrderResult);
    }

    @Operation(summary = "根据购物车信息生成订单")
    @RequestMapping(value = "/generateOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult generateOrder(@RequestBody OrderParam orderParam, HttpServletRequest request) {
        Map<String, Object> result = portalOrderService.generateOrder(orderParam);
        recordOrderItems(result.get("orderItemList"), null, BehaviorEventType.ORDER, "order_generate", request);
        return CommonResult.success(result, "下单成功");
    }

    @Operation(summary = "根据历史订单再来一单")
    @RequestMapping(value = "/reorder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult reorder(@RequestParam Long orderId, HttpServletRequest request) {
        Map<String, Object> result = portalOrderService.reorder(orderId);
        Long memberId = result.get("order") instanceof com.macro.mall.model.OmsOrder order ? order.getMemberId() : null;
        recordOrderItems(result.get("orderItemList"), memberId, BehaviorEventType.ORDER, "order_reorder", request);
        return CommonResult.success(result, "下单成功");
    }

    @Operation(summary = "用户支付成功的回调")
    @RequestMapping(value = "/paySuccess", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult paySuccess(@RequestParam Long orderId, @RequestParam Integer payType, HttpServletRequest request) {
        Integer count = portalOrderService.paySuccess(orderId,payType);
        if (count != null && count > 0) {
            OmsOrderDetail orderDetail = portalOrderService.detail(orderId);
            if (orderDetail != null) {
                if (Integer.valueOf(2).equals(orderDetail.getOrderType())) {
                    recordOrderItems(orderDetail.getOrderItemList(), orderDetail.getMemberId(), BehaviorEventType.CART, "order_reorder_pay", request);
                }
                recordOrderItems(orderDetail.getOrderItemList(), orderDetail.getMemberId(), BehaviorEventType.PAY, "order_pay", request);
            }
        }
        return CommonResult.success(count, "支付成功");
    }

    private void recordOrderItems(Object orderItemList, Long userId, String eventType, String sourcePage, HttpServletRequest request) {
        if (!(orderItemList instanceof List<?>)) {
            return;
        }
        for (Object item : (List<?>) orderItemList) {
            if (item instanceof OmsOrderItem) {
                OmsOrderItem orderItem = (OmsOrderItem) item;
                if (userId == null) {
                    behaviorEventRecorder.record(eventType, orderItem.getProductId(), orderItem.getProductCategoryId(), null, sourcePage, request);
                } else {
                    behaviorEventRecorder.record(userId, eventType, orderItem.getProductId(), orderItem.getProductCategoryId(), null, sourcePage, request);
                }
            }
        }
    }

    @Operation(summary = "自动取消超时订单")
    @RequestMapping(value = "/cancelTimeOutOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult cancelTimeOutOrder() {
        portalOrderService.cancelTimeOutOrder();
        return CommonResult.success(null);
    }

    @Operation(summary = "取消单个超时订单")
    @RequestMapping(value = "/cancelOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult cancelOrder(Long orderId) {
        portalOrderService.sendDelayMessageCancelOrder(orderId);
        return CommonResult.success(null);
    }

    @Operation(summary = "按状态分页获取用户订单列表")
    @Parameter(name = "status", description = "订单状态：-1->全部；0->待付款；1->待发货；2->已发货；3->已完成；4->已关闭",
            in = ParameterIn.QUERY, schema = @Schema(type = "integer",defaultValue = "-1",allowableValues = {"-1","0","1","2","3","4"}))
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<OmsOrderDetail>> list(@RequestParam Integer status,
                                                   @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                   @RequestParam(required = false, defaultValue = "5") Integer pageSize) {
        CommonPage<OmsOrderDetail> orderPage = portalOrderService.list(status,pageNum,pageSize);
        return CommonResult.success(orderPage);
    }

    @Operation(summary = "根据ID获取订单详情")
    @RequestMapping(value = "/detail/{orderId}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<OmsOrderDetail> detail(@PathVariable Long orderId) {
        OmsOrderDetail orderDetail = portalOrderService.detail(orderId);
        return CommonResult.success(orderDetail);
    }

    @Operation(summary = "用户取消订单")
    @RequestMapping(value = "/cancelUserOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult cancelUserOrder(Long orderId) {
        portalOrderService.cancelOrder(orderId);
        return CommonResult.success(null);
    }

    @Operation(summary = "用户确认收货")
    @RequestMapping(value = "/confirmReceiveOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult confirmReceiveOrder(Long orderId) {
        portalOrderService.confirmReceiveOrder(orderId);
        return CommonResult.success(null);
    }

    @Operation(summary = "用户删除订单")
    @RequestMapping(value = "/deleteOrder", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult deleteOrder(Long orderId) {
        portalOrderService.deleteOrder(orderId);
        return CommonResult.success(null);
    }
}
