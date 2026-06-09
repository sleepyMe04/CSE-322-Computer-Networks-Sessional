package Threading;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

public class Worker extends Thread {
    private Socket socket;

    public Worker(Socket socket) {
        this.socket = socket;
    }
    
    private void logActivity(String username, String fileName, String action, String status) {
        try {
            File userDir = new File("users/" + username);
            File logFile = new File(userDir, "history.log");
            
            // Get current date and time
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = sdf.format(new Date());
            
            // Create log entry
            String logEntry = timestamp + " | " + fileName + " | " + action + " | " + status + "\n";
            
            // Append to log file
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(logEntry);
            fw.close();
            
            System.out.println("Log entry added for " + username + ": " + action + " " + fileName);
            
        } catch (Exception e) {
            System.out.println("Failed to log activity: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleFileUpload(String username, ObjectOutputStream out, ObjectInputStream in) {
        String fileName = "unknown";
        long fileSize = 0;
        boolean bufferReserved = false;
        
        try {
            // Mark user as uploading
            synchronized(Server.class) {
                Server.uploadingUsers.add(username);
            }
            
            fileName = (String) in.readObject();
            fileSize = in.readLong();
            String access = (String) in.readObject();
            String requestID = (String) in.readObject();
            
            System.out.println("Upload request from " + username + ": " + fileName + " (" + fileSize + " bytes, " + access + ")");
            
            // Validate request ID if provided
            FileRequest relatedRequest = null;
            if(!requestID.equals("NONE")) {
                System.out.println("Upload is in response to request: " + requestID);
                
                synchronized(Server.class) {
                    List<FileRequest> userReqs = Server.userRequests.get(username);
                    if(userReqs != null) {
                        for(FileRequest req : userReqs) {
                            if(req.getRequestID().equals(requestID)) {
                                relatedRequest = req;
                                break;
                            }
                        }
                    }
                }
                
                if(relatedRequest == null) {
                    out.writeObject("UPLOAD_DENIED: Invalid request ID or request not found");
                    // Remove from uploading users before returning
                    synchronized(Server.class) {
                        Server.uploadingUsers.remove(username);
                    }
                    return;
                }
                
                // Force file to be public
                if(!access.equals("public")) {
                    System.out.println("Forcing file to be public (request response requirement)");
                    access = "public";
                }
            }
            
            // Check buffer size
            synchronized(Server.class) {
                if(Server.currentBufferSize + fileSize > Server.MAX_BUFFER_SIZE) {
                    out.writeObject("UPLOAD_DENIED: Buffer full");
                    // Remove from uploading users before returning
                    Server.uploadingUsers.remove(username);
                    return;
                }
                Server.currentBufferSize += fileSize;
                bufferReserved = true;
            }
            
            // Generate random chunk size
            Random random = new Random();
            int chunkSize = Server.MIN_CHUNK_SIZE + random.nextInt(Server.MAX_CHUNK_SIZE - Server.MIN_CHUNK_SIZE + 1);
            
            // Generate unique file ID
            String fileID = username + "_" + System.currentTimeMillis();
            
            // Send approval
            out.writeObject("UPLOAD_APPROVED:" + chunkSize + ":" + fileID);
            
            // Receive chunks
            List<byte[]> chunks = new ArrayList<>();
            long totalReceived = 0;
            
            while(true) {
                String messageType = (String) in.readObject();
                
                if(messageType.equals("CHUNK")) {
                    String receivedFileID = (String) in.readObject();
                    int chunkNumber = in.readInt();
                    int chunkLength = in.readInt();
                    byte[] chunkData = (byte[]) in.readObject();
                    
                    chunks.add(chunkData);
                    totalReceived += chunkLength;
                    
                    System.out.println("Received chunk " + chunkNumber + " (" + chunkLength + " bytes)");
                    out.writeObject("ACK_CHUNK_" + chunkNumber);
                }
                else if(messageType.equals("UPLOAD_COMPLETE")) {
                    String receivedFileID = (String) in.readObject();
                    
                    // Verify file size
                    if(totalReceived == fileSize) {
                        // Save file in appropriate folder
                        File userDir = new File("users/" + username);
                        File accessFolder = new File(userDir, access);
                        
                        if(!accessFolder.exists()) {
                            accessFolder.mkdirs();
                            System.out.println("Created " + access + " folder for " + username);
                        }
                        
                        File outputFile = new File(accessFolder, fileName);
                        
                        FileOutputStream fos = new FileOutputStream(outputFile);
                        for(byte[] chunk : chunks) {
                            fos.write(chunk);
                        }
                        fos.close();
                        
                        // Update buffer
                        synchronized(Server.class) {
                            Server.currentBufferSize -= fileSize;
                            bufferReserved = false;
                        }
                        
                        // Log successful upload
                        logActivity(username, fileName, "UPLOAD", "SUCCESS");
                        
                        // Send notification if this was in response to a request
                        if(relatedRequest != null) {
                            sendNotificationToRequester(relatedRequest, username, fileName);
                        }
                        
                        System.out.println("File saved: " + outputFile.getAbsolutePath());
                        out.writeObject("UPLOAD_SUCCESS: File uploaded successfully!");
                        
                    } else {
                        // Size mismatch - delete chunks
                        synchronized(Server.class) {
                            Server.currentBufferSize -= fileSize;
                            bufferReserved = false;
                        }
                        
                        logActivity(username, fileName, "UPLOAD", "FAILED");
                        out.writeObject("UPLOAD_FAILED: File size mismatch");
                    }
                    
                    break;
                }
            }
            
        } catch (Exception e) {
            // Client disconnected or error occurred during upload
            System.out.println("Upload interrupted for " + username + ": " + e.getMessage());
            
            // Clean up: Release buffer space and discard incomplete chunks
            if(bufferReserved) {
                synchronized(Server.class) {
                    Server.currentBufferSize -= fileSize;
                }
                System.out.println("Released buffer space for incomplete upload: " + fileSize + " bytes");
            }
            
            // Log failed upload
            logActivity(username, fileName, "UPLOAD", "FAILED - Connection lost");
            
            System.out.println("Discarded incomplete upload from " + username + ": " + fileName);
            
            // Try to send error message
            try {
                out.writeObject("UPLOAD_ERROR: " + e.getMessage());
            } catch (IOException ex) {
                System.out.println("Could not send error message to client (already disconnected)");
            }
        } finally {
            // IMPORTANT: Always remove user from uploading set
            synchronized(Server.class) {
                Server.uploadingUsers.remove(username);
            }
            System.out.println(username + " is no longer uploading");
        }
    }
    
    // NEW: Send notification to requester
    private void sendNotificationToRequester(FileRequest request, String uploader, String fileName) {
        try {
            String requester = request.getRequester();
            
            // Generate message ID
            String messageID;
            synchronized(Server.class) {
                messageID = "MSG" + Server.messageCounter;
                Server.messageCounter++;
            }
            
            // Get current timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = sdf.format(new Date());
            
            // Create message
            Message message = new Message(messageID, requester, uploader, request.getRequestID(), fileName, timestamp);
            
            // Add to requester's messages
            synchronized(Server.class) {
                if(!Server.userMessages.containsKey(requester)) {
                    Server.userMessages.put(requester, new ArrayList<>());
                }
                Server.userMessages.get(requester).add(message);
            }
            
            System.out.println("Notification sent to " + requester + ": " + uploader + " uploaded " + fileName + " for request " + request.getRequestID());
            
        } catch (Exception e) {
            System.out.println("Failed to send notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleListMyFiles(String username, ObjectOutputStream out) {
        try {
            File userDir = new File("users/" + username);
            File publicFolder = new File(userDir, "public");
            File privateFolder = new File(userDir, "private");
            
            StringBuilder fileList = new StringBuilder();
            fileList.append("\n=== My Files ===\n");
            
            boolean hasFiles = false;
            int count = 1;
            
            // List public files
            if(publicFolder.exists()) {
                File[] publicFiles = publicFolder.listFiles();
                if(publicFiles != null) {
                    for(File file : publicFiles) {
                        if(file.isFile()) {
                            hasFiles = true;
                            long sizeKB = file.length();
                            fileList.append(count).append(". ")
                                    .append(file.getName())
                                    .append(" (PUBLIC, ")
                                    .append(sizeKB)
                                    .append(" B)\n");
                            count++;
                        }
                    }
                }
            }
            
            // List private files
            if(privateFolder.exists()) {
                File[] privateFiles = privateFolder.listFiles();
                if(privateFiles != null) {
                    for(File file : privateFiles) {
                        if(file.isFile()) {
                            hasFiles = true;
                            long sizeKB = file.length();
                            fileList.append(count).append(". ")
                                    .append(file.getName())
                                    .append(" (PRIVATE, ")
                                    .append(sizeKB)
                                    .append(" B)\n");
                            count++;
                        }
                    }
                }
            }
            
            if(!hasFiles) {
                fileList.append("No files uploaded yet.\n");
            }

            out.writeObject(fileList.toString());
            
        } catch (Exception e) {
            try {
                out.writeObject("Error listing files: " + e.getMessage());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void handleListPublicFiles(String requestingUser, ObjectOutputStream out, ObjectInputStream in) {
        try {
            String targetUser = (String) in.readObject();
            
            // Check if target user exists
            synchronized(Server.class) {
                if(!Server.allUsers.contains(targetUser)) {
                    out.writeObject("Error: User '" + targetUser + "' does not exist.");
                    return;
                }
            }
            
            // Check if user is trying to list their own files
            if(targetUser.equals(requestingUser)) {
                out.writeObject("Error: Use 'List my files' to see your own files.");
                return;
            }
            
            File targetUserDir = new File("users/" + targetUser);
            File publicFolder = new File(targetUserDir, "public");
            
            StringBuilder fileList = new StringBuilder();
            fileList.append("\n=== Public Files of ").append(targetUser).append(" ===\n");
            
            boolean hasPublicFiles = false;
            int count = 1;
            
            if(publicFolder.exists()) {
                File[] publicFiles = publicFolder.listFiles();
                if(publicFiles != null) {
                    for(File file : publicFiles) {
                        if(file.isFile()) {
                            hasPublicFiles = true;
                            long sizeKB = file.length() / 1024;
                            fileList.append(count).append(". ")
                                    .append(file.getName())
                                    .append(" (")
                                    .append(sizeKB)
                                    .append(" KB)\n");
                            count++;
                        }
                    }
                }
            }
            
            if(!hasPublicFiles) {
                fileList.append("No public files available.\n");
            }
            
            out.writeObject(fileList.toString());
            
        } catch (Exception e) {
            try {
                out.writeObject("Error listing public files: " + e.getMessage());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void handleMakeRequest(String username, ObjectOutputStream out, ObjectInputStream in) {
        try {
            String description = (String) in.readObject();
            String recipient = (String) in.readObject();
            
            // Generate unique request ID
            String requestID;
            synchronized(Server.class) {
                requestID = "REQ" + Server.requestCounter;
                Server.requestCounter++;
            }
            
            // Get current timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = sdf.format(new Date());
            
            // Create file request
            FileRequest request = new FileRequest(requestID, username, recipient, description, timestamp);
            
            // Add to global requests list
            synchronized(Server.class) {
                Server.allRequests.add(request);
            }
            
            // Determine recipients and add to their request lists
            if(request.isBroadcast()) {
                // Broadcast to ALL users
                synchronized(Server.class) {
                    for(String user : Server.allUsers) {
                        if(!user.equals(username)) {  // Don't send to requester
                            if(!Server.userRequests.containsKey(user)) {
                                Server.userRequests.put(user, new ArrayList<>());
                            }
                            Server.userRequests.get(user).add(request);
                            System.out.println("  Added request to " + user + "'s list");  
                        }
                    }
                }
                System.out.println("Broadcast request " + requestID + " from " + username + " to ALL users");
                System.out.println("Total users who received request: " + (Server.allUsers.size() - 1));  
                out.writeObject("Request " + requestID + " broadcasted to all users successfully!");
                
            } else {
                // Unicast to specific user
                synchronized(Server.class) {
                    if(!Server.allUsers.contains(recipient)) {
                        out.writeObject("Error: User '" + recipient + "' does not exist.");
                        return;
                    }
                    
                    if(!Server.userRequests.containsKey(recipient)) {
                        Server.userRequests.put(recipient, new ArrayList<>());
                    }
                    Server.userRequests.get(recipient).add(request);
                }
                System.out.println("Unicast request " + requestID + " from " + username + " to " + recipient);
                out.writeObject("Request " + requestID + " sent to " + recipient + " successfully!");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            try {
                out.writeObject("Error making request: " + e.getMessage());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void handleViewRequests(String username, ObjectOutputStream out) {
        try {
            StringBuilder requestList = new StringBuilder();
            requestList.append("\n=== File Requests for ").append(username).append(" ===\n");
            
            List<FileRequest> myRequests;
            synchronized(Server.class) {
                myRequests = Server.userRequests.get(username);
                
                
                System.out.println(" Checking requests for " + username);
                System.out.println(" userRequests map contains key '" + username + "': " + Server.userRequests.containsKey(username));
                if(myRequests != null) {
                    System.out.println(" Number of requests: " + myRequests.size());
                } else {
                    System.out.println(" Request list is null");
                }
            }
            
            if(myRequests == null || myRequests.isEmpty()) {
                requestList.append("No file requests available.\n");
            } else {
                int count = 1;
                for(FileRequest req : myRequests) {
                    requestList.append(count).append(". ");
                    requestList.append(req.toCompactString());
                    if(req.isBroadcast()) {
                        requestList.append(" [BROADCAST]");
                    }
                    requestList.append("\n   Time: ").append(req.getTimestamp()).append("\n");
                    count++;
                }
            }
            
            out.writeObject(requestList.toString());
            System.out.println("Sent requests list to " + username);
            
        } catch (Exception e) {
            try {
                out.writeObject("Error retrieving requests: " + e.getMessage());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void handleViewMessages(String username, ObjectOutputStream out) {
        try {
            StringBuilder messageList = new StringBuilder();
            messageList.append("\n=== Messages for ").append(username).append(" ===\n");
            
            List<Message> myMessages;
            synchronized(Server.class) {
                myMessages = Server.userMessages.get(username);
            }
            
            if(myMessages == null || myMessages.isEmpty()) {
                messageList.append("No messages available.\n");
                out.writeObject(messageList.toString());
                return;
            }
            
            // Separate unread and read messages
            List<Message> unreadMessages = new ArrayList<>();
            List<Message> readMessages = new ArrayList<>();
            
            for(Message msg : myMessages) {
                if(msg.isRead()) {
                    readMessages.add(msg);
                } else {
                    unreadMessages.add(msg);
                }
            }
            
            // Display unread messages first
            if(!unreadMessages.isEmpty()) {
                messageList.append("\n--- UNREAD MESSAGES ---\n");
                int count = 1;
                for(Message msg : unreadMessages) {
                    messageList.append(count).append(". ");
                    messageList.append(msg.getSender()).append(" uploaded '");
                    messageList.append(msg.getFileName()).append("' for your request ");
                    messageList.append(msg.getRequestID());
                    messageList.append("\n   Time: ").append(msg.getTimestamp()).append("\n");
                    
                    // Mark as read
                    msg.markAsRead();
                    count++;
                }
                
                messageList.append("\n All unread messages marked as read.\n");
            } else {
                messageList.append("\nNo unread messages.\n");
            }
            
            // Display read messages
            if(!readMessages.isEmpty()) {
                messageList.append("\n--- READ MESSAGES ---\n");
                int count = 1;
                for(Message msg : readMessages) {
                    messageList.append(count).append(". ");
                    messageList.append(msg.getSender()).append(" uploaded '");
                    messageList.append(msg.getFileName()).append("' for your request ");
                    messageList.append(msg.getRequestID());
                    messageList.append("\n   Time: ").append(msg.getTimestamp()).append("\n");
                    count++;
                }
            }
            
            out.writeObject(messageList.toString());
            System.out.println("Sent messages to " + username + " (marked " + unreadMessages.size() + " as read)");
            
        } catch (Exception e) {
            try {
                out.writeObject("Error retrieving messages: " + e.getMessage());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void handleViewHistory(String username, ObjectOutputStream out) {
        try {
            File userDir = new File("users/" + username);
            File logFile = new File(userDir, "history.log");
            
            StringBuilder history = new StringBuilder();
            history.append("\n=== Upload/Download History for ").append(username).append(" ===\n");
            
            if(!logFile.exists() || logFile.length() == 0) {
                history.append("No history available yet.\n");
            } else {
                // Read the log file
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(logFile));
                String line;
                int entryNumber = 1;
                
                while((line = reader.readLine()) != null) {
                    // Format: timestamp | filename | action | status
                    history.append(entryNumber).append(". ").append(line).append("\n");
                    entryNumber++;
                }
                
                reader.close();
            }
            
            out.writeObject(history.toString());
            System.out.println("Sent history to " + username);
            
        } catch (Exception e) {
            try {
                out.writeObject("Error retrieving history: " + e.getMessage());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void handleFileDownload(String username, ObjectOutputStream out, ObjectInputStream in) {
        try {
            String fileOwner = (String) in.readObject();
            String fileName = (String) in.readObject();
            
            System.out.println("Download request from " + username + ": " + fileName + " (owner: " + fileOwner + ")");
            
            File fileToDownload = null;
            
            // Check if downloading own file
            if(fileOwner.equals(username)) {
                // Can download from both public and private folders
                File userDir = new File("users/" + username);
                File publicFile = new File(userDir, "public/" + fileName);
                File privateFile = new File(userDir, "private/" + fileName);
                
                if(publicFile.exists() && publicFile.isFile()) {
                    fileToDownload = publicFile;
                } else if(privateFile.exists() && privateFile.isFile()) {
                    fileToDownload = privateFile;
                }
                
            } else {
                // Can only download from public folder of other users
                File ownerDir = new File("users/" + fileOwner);
                File publicFile = new File(ownerDir, "public/" + fileName);
                
                if(publicFile.exists() && publicFile.isFile()) {
                    fileToDownload = publicFile;
                }
            }
            
            // Check if file exists
            if(fileToDownload == null) {
                out.writeObject("DOWNLOAD_ERROR: File not found or access denied");
                logActivity(username, fileName, "DOWNLOAD", "FAILED");
                return;
            }
            
            // Send download start message with file size
            long fileSize = fileToDownload.length();
            out.writeObject("DOWNLOAD_START:" + fileSize);
            
            // Read and send file in chunks
            FileInputStream fis = new FileInputStream(fileToDownload);
            byte[] buffer = new byte[Server.MAX_CHUNK_SIZE];
            int bytesRead;
            int chunkNumber = 1;
            
            while((bytesRead = fis.read(buffer)) != -1) {
                // Create chunk of actual size
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                
                // Send chunk (no waiting for ACK)
                out.writeObject("FILE_CHUNK");
                out.writeInt(bytesRead);
                out.writeObject(chunk);
                
                System.out.println("Sent chunk " + chunkNumber + " (" + bytesRead + " bytes) to " + username);
                chunkNumber++;
            }
            
            fis.close();
            
            // Send completion message
            out.writeObject("DOWNLOAD_COMPLETE");
            
            // Log successful download
            logActivity(username, fileName, "DOWNLOAD", "SUCCESS");
            
            System.out.println("Download complete for " + username + ": " + fileName);
            
        } catch (Exception e) {
            e.printStackTrace();
            try {
                out.writeObject("DOWNLOAD_ERROR: " + e.getMessage());
                String fileName = "unknown";
                try {
                    // Try to get filename from the exception context
                    fileName = (String) in.readObject();
                } catch(Exception ex) {
                    // Ignore
                }
                logActivity(username, fileName, "DOWNLOAD", "FAILED");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        String username = "unknown";
        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject("Enter your username:");
            username = (String) in.readObject();

            boolean isUsernameTaken = false;
            synchronized(Server.class) {
                if(Server.activeUsers.contains(username)) {
                    isUsernameTaken = true;
                } else {
                    Server.activeUsers.add(username);
                }
                Server.allUsers.add(username);
            }

            // username taken case
            if(isUsernameTaken) {
                out.writeObject("Username already taken. Connection closing.");
                socket.close();
                return;
            }

            // Username accepted
            System.out.println("User connected: " + username);
            
            // Create user directory
            File userDir = new File("users/" + username);
            if(!userDir.exists()) {
                userDir.mkdirs();
                System.out.println("User directory created: " + userDir.getAbsolutePath());
            }
            
            // Check for unread messages and notify user
            int unreadCount = 0;
            synchronized(Server.class) {
                List<Message> messages = Server.userMessages.get(username);
                if(messages != null) {
                    for(Message msg : messages) {
                        if(!msg.isRead()) {
                            unreadCount++;
                        }
                    }
                }
            }
            
            String welcomeMsg = "Welcome " + username;
            if(unreadCount > 0) {
                welcomeMsg += "\n You have " + unreadCount + " unread message(s)! Use option 9 to view them.";
            }
            out.writeObject(welcomeMsg);
            
            boolean running = true;
            while (running) {
                try {
                    String command = (String) in.readObject();
                    System.out.println("Received command from " + username + ": " + command);
                    
                    if(command.equals("LOG_OUT")) {
                        // Check if user is currently uploading
                        boolean isUploading;
                        synchronized(Server.class) {
                            isUploading = Server.uploadingUsers.contains(username);
                        }
                        
                        if(isUploading) {
                            out.writeObject("Cannot logout: Upload in progress. Please wait for upload to complete or it will be cancelled.");
                            System.out.println(username + " tried to logout during upload - denied");
                            continue;
                        }
                        
                        running = false;
                        
                        // Remove user from active users
                        synchronized(Server.class) {
                            Server.activeUsers.remove(username);
                        }
                        
                        out.writeObject(username + " logged out successfully.");
                        System.out.println(username + " logged out successfully.");
                        socket.close();
                    }
                    else if(command.equals("LIST_USERS")) {
                        StringBuilder clientList = new StringBuilder();
                        clientList.append("\n=== All Clients ===\n");
                        
                        synchronized(Server.class) {
                            for(String user : Server.allUsers) {
                                if(Server.activeUsers.contains(user)) {
                                    clientList.append("• ").append(user).append(" (Online)\n");
                                } else {
                                    clientList.append("• ").append(user).append(" (Offline)\n");
                                }
                            }
                        }
                        
                        out.writeObject(clientList.toString());
                    }
                    else if(command.equals("UPLOAD_FILE")) {
                        handleFileUpload(username, out, in);
                    }
                    else if(command.equals("LIST_MY_FILES")) {
                        handleListMyFiles(username, out);
                    }
                    else if(command.equals("LIST_PUBLIC_FILES")) {
                        handleListPublicFiles(username, out, in);
                    }
                    else if(command.equals("DOWNLOAD_FILE")) {
                        handleFileDownload(username, out, in);
                    }
                    else if(command.equals("VIEW_HISTORY")) {
                        handleViewHistory(username, out);
                    }
                    else if(command.equals("MAKE_REQUEST")) {
                        handleMakeRequest(username, out, in);
                    }
                    else if(command.equals("VIEW_REQUESTS")) {
                        handleViewRequests(username, out);
                    }
                    else if(command.equals("VIEW_MESSAGES")) {
                        handleViewMessages(username, out);
                    }
                    else {
                        out.writeObject("Unknown command");
                    }
                    
                } catch (IOException e) {
                    // IOException means client disconnected
                    System.out.println("Client disconnected: " + username);
                    running = false;  // Exit the loop
                    break;
                } catch (ClassNotFoundException e) {
                    System.out.println("Error reading command from " + username + ": " + e.getMessage());
                    running = false;
                    break;
                }
            }
            
        } catch (Exception e) {
            System.out.println("Connection error for " + username + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup when client disconnects (for any reason)
            synchronized(Server.class) {
                Server.activeUsers.remove(username);
                Server.uploadingUsers.remove(username);  // Clean up if they were uploading
            }
            System.out.println(username + " disconnected and removed from active users");
            
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Error closing socket: " + e.getMessage());
            }
        }
    }
}