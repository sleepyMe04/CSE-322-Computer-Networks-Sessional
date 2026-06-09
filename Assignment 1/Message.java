package Threading;

import java.io.Serializable;

public class Message implements Serializable {
    private String messageID;
    private String recipient;      // Who receives the message
    private String sender;         // Who uploaded the file
    private String requestID;      // Which request this is for
    private String fileName;       // Name of uploaded file
    private String timestamp;      // When the file was uploaded
    private boolean isRead;        // Whether message has been read
    
    public Message(String messageID, String recipient, String sender, String requestID, String fileName, String timestamp) {
        this.messageID = messageID;
        this.recipient = recipient;
        this.sender = sender;
        this.requestID = requestID;
        this.fileName = fileName;
        this.timestamp = timestamp;
        this.isRead = false;
    }
    
    // Getters
    public String getMessageID() {
        return messageID;
    }
    
    public String getRecipient() {
        return recipient;
    }
    
    public String getSender() {
        return sender;
    }
    
    public String getRequestID() {
        return requestID;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public boolean isRead() {
        return isRead;
    }
    
    public void markAsRead() {
        this.isRead = true;
    }
    
    @Override
    public String toString() {
        String status = isRead ? "[READ]" : "[UNREAD]";
        return status + " " + sender + " uploaded '" + fileName + "' for your request " + requestID + " (" + timestamp + ")";
    }
}