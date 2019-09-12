

import com.alibaba.dubbo.config.annotation.Reference;

import org.apache.activemq.command.ActiveMQMapMessage;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;

@Component
public class PaymentConsumer {

    @Reference
    private PaymentService paymentService;


    @JmsListener(destination = "PAYMENT_RESULT_CHECK_QUEUE",containerFactory = "jmsQueueListener")
    public  void  consumerPaymentResult(ActiveMQMapMessage mapMessage) throws JMSException {
        String outTradeNo = mapMessage.getString("outTradeNo");
        int delaySec = mapMessage.getInt("delaySec");
        int checkCount = mapMessage.getInt("checkCount");

        //check 支付宝的支付结果 主要条件是outTradeNo
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOutTradeNo(outTradeNo);
        System.out.println("开始查询");

        //直接查询即可
        boolean flag = paymentService.checkPayment(paymentInfo);

        //如果 flag为 false 没有付款，
        if(!flag && checkCount>0){
            System.out.println("查询结果"+flag);
            //调用check方法继续查询
            paymentService.sendDelayPaymentResult(outTradeNo,delaySec,checkCount-1);
        }

    }

}
