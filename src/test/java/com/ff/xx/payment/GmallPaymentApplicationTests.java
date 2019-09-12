
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.jms.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GmallPaymentApplicationTests {

	@Autowired
	private ActiveMQUtil activeMQUtil;

	@Test
	public void contextLoads() {
	}

	@Test
	public void test() throws JMSException {

		Connection connection = activeMQUtil.getConnection();

		//打开连接
		connection.start();

		//创建session
		Session session = connection.createSession(false,Session.AUTO_ACKNOWLEDGE);

		//创建队列
		Queue queue = session.createQueue("Atguigu");

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
	}

}
