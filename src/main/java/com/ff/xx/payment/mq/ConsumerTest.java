
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.jms.*;

public class ConsumerTest {

//    @Autowired
//    private static ActiveMQUtil activeMQUtil;

    public static void main(String[] args) throws JMSException {
        //创建连接,我们需要使用默认的用户名和密码
        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory(ActiveMQConnection.DEFAULT_USER, ActiveMQConnection.DEFAULT_PASSWORD, "tcp://192.168.111.132:61616");

        //创建连接
        Connection connection = activeMQConnectionFactory.createConnection();

        //Connection connection = activeMQUtil.getConnection();

        //打开连接
        connection.start();

        //创建session
        Session session = connection.createSession(false,Session.AUTO_ACKNOWLEDGE);

        //创建队列
        Queue queue = session.createQueue("dd");

        //创建消费者
        MessageConsumer consumer = session.createConsumer(queue);

        //创建消息监听器，只要有消息发送，立即建通消费
        consumer.setMessageListener(new MessageListener() {
            //message 消息的父类
            @Override
            public void onMessage(Message message) {
                if(message instanceof TextMessage){
                    try {
                        String text = ((TextMessage) message).getText();
                        System.out.println("消息："+text);
                    } catch (JMSException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        //关闭
        consumer.close();
        session.close();


    }
}
