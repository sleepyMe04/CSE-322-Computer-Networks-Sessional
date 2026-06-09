package Threading;

import java.io.Serializable;

public class FileMetadata implements Serializable {
    private String fileName;
    private String access;  // "public" or "private"
    private long fileSize;
    private long uploadTime;
    
    public FileMetadata(String fileName, String access, long fileSize) {
        this.fileName = fileName;
        this.access = access;
        this.fileSize = fileSize;
        this.uploadTime = System.currentTimeMillis();
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public String getAccess() {
        return access;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public long getUploadTime() {
        return uploadTime;
    }
}