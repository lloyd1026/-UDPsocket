import java.net.*;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class UDPServer {
    private static final int SERVER_PORT = 12345;           // 服务器端口号
    private static final double PACKET_LOSS_RATE = 0.6;     // 丢包率
    // messageType(2 Byte)
    private static final short _serverToClient = 0x01;      // 服务器给客户端
    private static final short _clientToServer = 0x02;      // 客户端给服务器
    private static final short _synchronized = 0x03;        // 客户端的同步请求连接报文
    private static final short _ack = 0x04;                 // 服务器对于客户端的syn的ack
    private static final short _fin = 0x05;                 // 第一次挥手
    private static final short _finAck = 0x06;              // 第二次挥手：对第一次挥手的ack
    private static final short _close = 0x07;               // 第三次挥手：准备关闭连接
    private static final short _closeAck = 0x08;            // 第四次挥手：服务器会等一段时间关闭，由于这里需要同时处理多个客户端，所以不用关，客户端收到后就关闭

    public static void main(String[] args) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(SERVER_PORT);
            Random random = new Random();
            byte[] receiveData = new byte[1024];

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length); // DatagramPacket对象，接收UDP数据报文，并存进指定好的字节数组，直接通过对象来访问数据
                socket.receive(receivePacket);                                                      // 阻塞


                // 对客户端封包进行解包
                ByteBuffer wrapped = ByteBuffer.wrap(receivePacket.getData());
                short seqNo = wrapped.getShort();
                byte version = wrapped.get();
                short packageType = wrapped.getShort();
                short sendType = _serverToClient;
                if(packageType == _synchronized){
                    sendType = _ack;
                }else if(packageType == _fin){
                    sendType = _finAck;
                } else if(packageType == _close){
                    sendType = _closeAck;
                } else{
                    // 普通报文模拟丢包
                    if (random.nextDouble() < PACKET_LOSS_RATE) continue;   // 人为丢包
                }
                // 准备回包内容
                String currentTime = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
                byte[] sendData = createResponsePacket(seqNo, version, sendType, currentTime);
                // 获取客户端IP 端口号，将报文发回去
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();
                // 发包
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                socket.send(sendPacket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private static byte[] createResponsePacket(short seqNo, byte version, short packageType, String serverTime) {
        // server -> client
        // 报文格式 [Seq no(2B) | Ver(1B) | type(2B) | systemTime(12B) | others...]
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putShort(seqNo);
        buffer.put(version);
        buffer.putShort(packageType);
        buffer.put(serverTime.getBytes());
        // content

        return buffer.array();
    }

}
