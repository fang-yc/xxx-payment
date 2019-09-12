

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PaymentController {

    @Reference
    private PaymentService paymentService;

    @Reference
    private OrderService orderService;

    @Autowired
    private AlipayClient alipayClient;


    @RequestMapping("index")
    public String index(String orderId,HttpServletRequest request){
        //得到orderId
        System.out.println("orderId "+orderId);
        request.setAttribute("orderId",orderId);

        //得到总金额,应该再orderInfo中，orderId查询orderInfo的总金额
        //select * from orderInfo where orderId = ？
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);

        //保存总金额
        request.setAttribute("totalAmount",orderInfo.getTotalAmount());

        return "index";
    }

    @RequestMapping("alipay/submit")
    @ResponseBody
    public String alipay(HttpServletRequest request, HttpServletResponse response){
        //提交操作，做一个交易的记录，主要用于对账！幂等性！
        //将交易记录放入那张表 payment_info
        String orderId = request.getParameter("orderId");

        //取得到orderInfo 的数据
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderId(orderId);
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());

        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());
        paymentInfo.setSubject("购买手机");
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID);
        paymentInfo.setCreateTime(new Date());

        paymentService.savePaymentInfo(paymentInfo);




        //主要生成二维码，涉及到参数，签名
        //注入到spring容器中
            //AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do", APP_ID, APP_PRIVATE_KEY, FORMAT, CHARSET, ALIPAY_PUBLIC_KEY, SIGN_TYPE); //获得初始化的AlipayClient
            //
            AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();//创建API对应的request
            //同步回调地址
            alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);
            //异步回调地址
            alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url);//在公共参数中设置回跳和通知地址
            //将参数： 第三方交易编号，商品编码，
            //声明一个map集合
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no",paymentInfo.getOutTradeNo());
        map.put("product_code","FAST_INSTANT_TRADE_PAY");
        map.put("subject",paymentInfo.getSubject());
        map.put("total_amount",paymentInfo.getTotalAmount());


        //将map转换为字符串
        String mapJson = JSON.toJSONString(map);


//        alipayRequest.setBizContent("{" +
//                    "    \"out_trade_no\":\"20150320010101001\"," +
//                    "    \"product_code\":\"FAST_INSTANT_TRADE_PAY\"," +
//                    "    \"total_amount\":88.88," +
//                    "    \"subject\":\"Iphone6 16G\"," +
//                    "    \"body\":\"Iphone6 16G\"," +
//                    "    \"passback_params\":\"merchantBizType%3d3C%26merchantBizNo%3d2016010101111\"," +
//                    "    \"extend_params\":{" +
//                    "    \"sys_service_provider_id\":\"2088511833207846\"" +
//                    "    }"+
//                    "  }");//填充业务参数

        alipayRequest.setBizContent(mapJson);

            String form="";
            try {
                form = alipayClient.pageExecute(alipayRequest).getBody(); //调用SDK生成表单
            } catch (AlipayApiException e) {
                e.printStackTrace();
            }
            //设置页面的ContentType，又设置了页面的字符集
            response.setContentType("text/html;charset=UTF-8");
            //输出表单

        //生成二维码的时候，调用延迟队列
        paymentService.sendDelayPaymentResult(paymentInfo.getOutTradeNo(),15,3);

        return form;
    }

    //同步回调
    @RequestMapping("/alipay/callback/return")
    public String callbackReturn(){
        //给用户看的 url 路径 http://order.gmall.com/trade
        return "redirect:"+AlipayConfig.return_order_url;
    }

    //异步回调
    @RequestMapping("/alipay/callback/notify")
    @ResponseBody
    public String paymentNotify(@RequestParam Map<String,String> paramMap, HttpServletRequest request) throws AlipayApiException {
        boolean flag = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key, "utf-8",AlipayConfig.sign_type);
        //
        if(!flag){
            return "fail";
        }
        //获取交易状态
        String trade_status = paramMap.get("trade_status");
        //payment_status 为支付状态 PAID支付！ 通过 out_trade_no 第三方交易编号
        if("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)){
            //判断当前用户 PAID,UNPAID
            String out_trade_no = paramMap.get("out_trade_no");
            //select * from paymentInfo where out_trade_no = out_trade_no
            PaymentInfo paymentInfoQuery = new PaymentInfo();
            paymentInfoQuery.setOutTradeNo(out_trade_no);

            PaymentInfo paymentInfo = paymentService.getPaymentInfo(paymentInfoQuery);

            //当支付宝发现该笔订单已经关闭，或者已经付款了，则告诉商家付款失败
            if(paymentInfo.getPaymentStatus()==PaymentStatus.ClOSED || paymentInfo.getPaymentStatus()==PaymentStatus.PAID){
                return "fail";
            }else{
                //未付款的时候
                PaymentInfo paymentInfoUpd  = new PaymentInfo();
                // 设置状态
                paymentInfoUpd.setPaymentStatus(PaymentStatus.PAID);
                // 设置创建时间
                paymentInfoUpd.setCallbackTime(new Date());
                // 异步回调的内容
                paymentInfoUpd.setCallbackContent(paramMap.toString());

                //update paymentInfo set payment_status = PAID where out_trade_no = out_trade_no
                paymentService.updatePaymentInfo(out_trade_no,paymentInfoUpd);

                //我们应该发送通知，给支付模块， 参数 orderId ， result
                paymentService.sendPaymentResult(paymentInfoUpd,"success");


                return "success";
            }
        }
        return "fail";
    }

    //手动写一个控制器
    @RequestMapping("sendPaymentResult")
    @ResponseBody
    public String sendPaymentResult(PaymentInfo paymentInfo,@RequestParam("result") String result){
        paymentService.sendPaymentResult(paymentInfo,result);
        return "sent payment result";
    }

    /**
     *  查看那个一个订单是否付款成功！
     * @return
     */
    @RequestMapping("queryPaymentResult")
    @ResponseBody
    public String queryPaymentResult(String orderId){
        // 根据orderId 查询出去交易对象
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderId(orderId);
        PaymentInfo paymentInfoQuery = paymentService.getPaymentInfo(paymentInfo);
        // 调用服务层的方法，得到交易对象中的out_trade_no
        boolean flag = paymentService.checkPayment(paymentInfoQuery);
        return ""+flag;

    }

}
