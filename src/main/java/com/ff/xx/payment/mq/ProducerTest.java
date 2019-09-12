

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTextMessage;

import javax.jms.*;

//消息提供者
public class ProducerTest {
    public static void main(String[] args) throws JMSException {
        //创建连接工厂   类似于mybatis
        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory("tcp://192.168.111.132:61616");

        //创建一个连接
        Connection connection = activeMQConnectionFactory.createConnection();

        //打开连接
        connection.start();

        //创建session
        //createSession 中第一个参数表示是否开启事务 , 第二个参数根据前面选择的事务，对应选中应对事务的解决方案,事务指的是发送的消息
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        //创建队列
        Queue queue = session.createQueue("dd");

        //创建消息的提供者
        MessageProducer producer = session.createProducer(queue);

        //创建sessage对象
        ActiveMQTextMessage activeMQTextMessage = new ActiveMQTextMessage();
        activeMQTextMessage.setText("hello! BITCH!");
        //发送消息
        producer.send(activeMQTextMessage);

        //关闭操作
        producer.close();
        session.close();
        connection.close();


    }
}
