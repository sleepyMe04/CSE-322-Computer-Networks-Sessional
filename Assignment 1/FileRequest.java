

package Threading;

import java.io.Serializable;

public class FileRequest implements Serializable {
    private String requestID;
    private String requester;      // Who made the request
    private String recipient;      // Who should fulfill it (username or "ALL")
    private String description;    // What file is needed
    private String timestamp;      // When the request was made
    private boolean isBroadcast;   // True if recipient is "ALL"
    
    public FileRequest(String requestID, String requester, String recipient, String description, String timestamp) {
        this.requestID = requestID;
        this.requester = requester;
        this.recipient = recipient;
        this.description = description;
        this.timestamp = timestamp;
        this.isBroadcast = recipient.equalsIgnoreCase("ALL");
    }
    
    // Getters
    public String getRequestID() {
        return requestID;
    }
    
    public String getRequester() {
        return requester;
    }
    
    public String getRecipient() {
        return recipient;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public boolean isBroadcast() {
        return isBroadcast;
    }
    
    @Override
    public String toString() {
        return "Request ID: " + requestID + 
               "\nFrom: " + requester + 
               "\nTo: " + recipient + 
               "\nDescription: " + description + 
               "\nTime: " + timestamp;
    }
    
    // Compact format for listing
    public String toCompactString() {
        return "[" + requestID + "] From " + requester + ": " + description;
    }
 
    
}
