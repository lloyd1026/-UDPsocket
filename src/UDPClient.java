import java.net.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class UDPClient {
    private static final int TIMEOUT = 100;                 // 100 ms 超时时间
    private static final int MAX_RETRIES = 3;               // 最大重传次数
    private static final int TOTAL_PACKETS = 12;           // 发包总量
    private static final byte VERSION = 0x2;                // 版本号
    private static final int serverPort = 12345;            // server port
    private static final String serverIp = "0.0.0.0";       // server IP

    // messageType(2 Byte)
    private static final short _serverToClient = 0x01;      // 服务器给客户端
    private static final short _clientToServer = 0x02;      // 客户端给服务器
    private static final short _synchronized = 0x03;        // 客户端的同步请求连接报文
    private static final short _ack = 0x04;                 // 服务器对客户端的ack
    private static final short _fin = 0x05;                 // 第一次挥手
    private static final short _finAck = 0x06;              // 第二次挥手：对第一次挥手的ack
    private static final short _close = 0x07;               // 第三次挥手：准备关闭连接
    private static final short _closeAck = 0x08;            // 第四次挥手：服务器会等一段时间关闭，由于这里需要同时处理多个客户端，所以不用关，客户端收到后就关闭

    public static void main(String[] args) {
//        if(args.length != 2){
//            System.out.println("Only two usages <serverIP> <serverPort> needed!");
//            return;
//        }
//        String serverIp = args[0];
//        int serverPort = Integer.parseInt(args[1]);

        DatagramSocket socket = null;
        int receivedPackets = 0;                            // 收包量
        long[] rtts = new long[TOTAL_PACKETS];              // 发包rtt数组
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        Date parsedTime;
        long firstResponseTime = 0;
        long lastResponseTime = 0;

        try {
            socket = new DatagramSocket();
            InetAddress serverAddress = InetAddress.getByName(serverIp);

            // 模拟TCP的连接过程
            if(exchangePackets((short)(0), socket, serverAddress, _synchronized, _ack)){
                System.out.println("received Ack from Server");
            } else{
                System.out.println("connection fail [第一次握手]");
                return;
            }

            // 开始发包
            for (short seqNo = 1; seqNo <= TOTAL_PACKETS; seqNo++) {
                byte[] sendData = createPacket(seqNo, VERSION, _clientToServer, "Data：ABCDEFGHIJKLMN");
                // 创建UDP报文
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);

                // 丢包尝试重传
                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                    socket.send(sendPacket);
                    long startTime = System.currentTimeMillis();// 发包的开始时间
                    try {
                        socket.setSoTimeout(TIMEOUT);           // 设置等待数据包的超时时间
                        byte[] receiveData = new byte[1024];
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        socket.receive(receivePacket);          // 阻塞方法，接收或者超时

                        // 收到服务器的报文
                        long endTime = System.currentTimeMillis();
                        long rtt = endTime - startTime;
                        rtts[receivedPackets] = rtt;
                        receivedPackets++;
                        // 开始对服务器的报文解包
                        ByteBuffer wrapped = ByteBuffer.wrap(receivePacket.getData());
                        short receivedSeqNo = wrapped.getShort();
                        wrapped.get();
                        short receivedType = wrapped.getShort();
                        if(receivedType != _serverToClient) continue;
                        // 客户端收包时间
                        String serverTime = new String(receivePacket.getData(), 5, 12).trim();
                        parsedTime = dateFormat.parse(serverTime);
                        if (receivedPackets == 1) {
                            firstResponseTime = parsedTime.getTime();
                        } else {
                            // 不确定还能不能收到下一份包
                            lastResponseTime = parsedTime.getTime();
                        }
                        // 输出报文接收信息
                        System.out.println("Sequence No: " + receivedSeqNo + "(" + attempt +")" + ", Server IP: " + receivePacket.getAddress() +
                                ", Server Port: " + receivePacket.getPort() + ", RTT: " + rtt + " ms, Server Time: " + serverTime);

                        break;
                    } catch (SocketTimeoutException e) {
                        if (attempt == MAX_RETRIES) {
                            if(seqNo == 1) {
                                System.out.println("connection fail [第三次握手]");
                                return;          // 相当于第三次握手失败
                            }
                            System.out.println("Sequence No: " + seqNo + ", Request Timeout");
                        }
                    }
                }
            }

            // 模拟四次挥手
            if(exchangePackets((short)(TOTAL_PACKETS + 1), socket, serverAddress, _fin, _finAck)){
                System.out.println("received finAck from Server");
            } else{
                System.out.println("connection fail [挥手]");
            }

            if(exchangePackets((short)(TOTAL_PACKETS + 2), socket, serverAddress, _close, _closeAck)){
                System.out.println("received closeAck from Server");
            } else{
                System.out.println("connection fail [挥手]");
            }

            // Print summary
            double lossRate = 1 - ((double) receivedPackets / TOTAL_PACKETS);
            long maxRTT = Arrays.stream(rtts).max().orElse(0);
            long minRTT = Arrays.stream(rtts).filter(x -> x > 0).min().orElse(0);
            double avgRTT = Arrays.stream(rtts).average().orElse(0);
            double rttStdDev = Math.sqrt(Arrays.stream(rtts).filter(x -> x > 0).mapToDouble(x -> Math.pow(x - avgRTT, 2)).average().orElse(0));
            long totalServerResponseTime = lastResponseTime - firstResponseTime;    // server的整体响应时间，server最后一次response的系统时间与第一次response的系统时间

            System.out.println("Received UDP Packets: " + receivedPackets);
            System.out.println("Loss Rate: " + (lossRate * 100) + "%");
            System.out.println("Max RTT: " + maxRTT + " ms");
            System.out.println("Min RTT: " + minRTT + " ms");
            System.out.println("Average RTT: " + avgRTT + " ms");
            System.out.println("RTT Standard Deviation: " + rttStdDev + " ms");
            System.out.println("Total Server Response Time: " + totalServerResponseTime + " ms");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private static byte[] createPacket(short seqNo, byte version, short messageType, String data) {
        // client -> server
        // 报文格式 [Seq no(2B) | Ver(1B) | type(2B) | others...]
        // ver 版本号固定为2
        // 头部字段
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putShort(seqNo);
        buffer.put(version);
        buffer.putShort(messageType);

        // content
        buffer.put(data.getBytes());
        return buffer.array();
    }

    private static  boolean exchangePackets (short seqNo, DatagramSocket socket, InetAddress serverAddress, short sendType, short receiveType) throws Exception{
        for(int attempt = 1; attempt <= MAX_RETRIES; attempt++){
            byte[] SYN_packet = createPacket(seqNo, VERSION, sendType, "");
            DatagramPacket sendPacket = new DatagramPacket(SYN_packet, SYN_packet.length, serverAddress, serverPort);
            socket.send(sendPacket);
            try{
                socket.setSoTimeout(TIMEOUT);                                   // 设置等待数据包的超时时间
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);                                  // 阻塞方法，接收或者超时
                ByteBuffer wrapped = ByteBuffer.wrap(receivePacket.getData());
                wrapped.getShort();
                wrapped.get();
                short receivedType = wrapped.getShort();
                if(receivedType == receiveType){
                    break;
                }
            } catch (SocketTimeoutException e) {
                if (attempt == MAX_RETRIES) {
                    return false;
                }
            }
        }
        return true;
    }
}
