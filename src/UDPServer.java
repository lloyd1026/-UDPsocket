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

    public static void main(String[] args) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(SERVER_PORT);
            Random random = new Random();
            byte[] receiveData = new byte[1024];

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length); // DatagramPacket对象，接收UDP数据报文，并存进指定好的字节数组，直接通过对象来访问数据
                socket.receive(receivePacket);

                if (random.nextDouble() < PACKET_LOSS_RATE) {
                    // 人为丢包
                    continue;
                }
                // 对客户端封包进行解包
                ByteBuffer wrapped = ByteBuffer.wrap(receivePacket.getData());
                short seqNo = wrapped.getShort();
                byte version = wrapped.get();
                short packageType = wrapped.getShort();
                if(packageType != _clientToServer) continue;
                // 准备回包内容
                String currentTime = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
                byte[] sendData = createResponsePacket(seqNo, version, _serverToClient, currentTime);
                // 获取客户端IP 端口号，将ack报文发回去
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();
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
        // 报文格式 [Seq no(2B) | Ver(1B) | type(2B) | systemTime(12B) | others...(200B)]
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putShort(seqNo);
        buffer.put(version);
        buffer.putShort(packageType);
        buffer.put(serverTime.getBytes());
        // content

        return buffer.array();
    }

}
