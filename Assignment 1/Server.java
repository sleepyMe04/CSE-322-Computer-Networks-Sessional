package Threading;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Server {
    public static Set<String> activeUsers=new HashSet<>();
    public static Set<String> allUsers=new HashSet<>();
    public static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024;  // 10 MB
    public static final int MIN_CHUNK_SIZE = 50 * 1024;          // 50 KB
    public static final int MAX_CHUNK_SIZE = 500 * 1024;         // 500 KB
    public static int currentBufferSize = 0; 
    // File requests storage
    public static List<FileRequest> allRequests = new ArrayList<>();
    
    // User-specific requests (for each user to see their received requests)
    public static Map<String, List<FileRequest>> userRequests = new HashMap<>();
    
    // Messages for each user (notifications when files are uploaded for their requests)
    public static Map<String, List<Message>> userMessages = new HashMap<>();

    // Tracking users currently uploading (to prevent logout during upload)
    public static Set<String> uploadingUsers = new HashSet<>();
    
    // Request ID counter
    public static int requestCounter = 1;
    
    // Message ID counter
    public static int messageCounter = 1;
    public static void main(String[] args) throws IOException {
        ServerSocket welcomeSocket = new ServerSocket(6666);

        while (true) {
            System.out.println("Waiting for connection...");
            Socket socket = welcomeSocket.accept();
            System.out.println("Connection established");
        
            Thread worker = new Worker(socket);
            worker.start();
        }
    }
}
