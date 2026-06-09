package Threading;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static void handleFileUpload(ObjectOutputStream out, ObjectInputStream in, Scanner scanner) {
        try {
            System.out.print("Enter file path: ");
            String filePath = scanner.nextLine();
            
            File file = new File(filePath);
            if(!file.exists() || !file.isFile()) {
                System.out.println("File does not exist!");
                return;
            }
            
            // Ask if this is in response to a request
            System.out.print("Is this in response to a request? (yes/no): ");
            String isResponse = scanner.nextLine().toLowerCase();
            
            String access;
            String requestID = null;
            
            if(isResponse.equals("yes")) {
                // Upload in response to request
                System.out.print("Enter request ID: ");
                requestID = scanner.nextLine();
                
                // File will be automatically set as public
                access = "public";
                System.out.println("Note: File will be uploaded as PUBLIC (required for request responses)");
                
            } else {
                // Normal upload
                System.out.print("File access (public/private): ");
                access = scanner.nextLine().toLowerCase();
                if(!access.equals("public") && !access.equals("private")) {
                    System.out.println("Invalid access type!");
                    return;
                }
            }
            
            // Send upload request to server
            out.writeObject("UPLOAD_FILE");
            out.writeObject(file.getName());
            out.writeLong(file.length());
            out.writeObject(access);
            out.writeObject(requestID != null ? requestID : "NONE");  // Send request ID or "NONE"
            
            // Wait for server response
            String response = (String) in.readObject();
            System.out.println("Server: " + response);
            
            if(response.startsWith("UPLOAD_APPROVED")) {
                // Extract chunk size and fileID from response
                String[] parts = response.split(":");
                int chunkSize = Integer.parseInt(parts[1]);
                String fileID = parts[2];
                
                System.out.println("Chunk size: " + chunkSize + " bytes");
                System.out.println("File ID: " + fileID);
                
                // Read file and send chunks
                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[chunkSize];
                int bytesRead;
                int chunkNumber = 1;
                
                while((bytesRead = fis.read(buffer)) != -1) {
                    // Send chunk
                    byte[] chunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                    
                    out.writeObject("CHUNK");
                    out.writeObject(fileID);
                    out.writeInt(chunkNumber);
                    out.writeInt(bytesRead);
                    out.writeObject(chunk);
                    
                    System.out.println("Sent chunk " + chunkNumber + " (" + bytesRead + " bytes)");
                    
                    // Wait for acknowledgment
                    String ack = (String) in.readObject();
                    System.out.println("Server: " + ack);
                    
                    chunkNumber++;
                }
                
                fis.close();
                
                // Send completion message
                out.writeObject("UPLOAD_COMPLETE");
                out.writeObject(fileID);
                
                // Wait for final response
                String finalResponse = (String) in.readObject();
                System.out.println("Server: " + finalResponse);
            }
            
        } catch (Exception e) {
            System.out.println("Upload failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void handleFileDownload(ObjectOutputStream out, ObjectInputStream in, Scanner scanner, String username) {
        try {
            System.out.println("\n=== Download File ===");
            System.out.println("1. Download my own file");
            System.out.println("2. Download someone's public file");
            System.out.print("Choose option: ");
            
            String downloadChoice = scanner.nextLine();
            
            String fileOwner;
            String fileName;
            
            if(downloadChoice.equals("1")) {
                // Download own file
                fileOwner = username;
                
                //  get list of own files
                out.writeObject("LIST_MY_FILES");
                String fileList = (String) in.readObject();
                System.out.println(fileList);
                
                System.out.print("Enter filename to download: ");
                fileName = scanner.nextLine();
                
            } else if(downloadChoice.equals("2")) {
                // Download public file from another user
                System.out.print("Enter username: ");
                fileOwner = scanner.nextLine();
                
                //  get list of their public files
                out.writeObject("LIST_PUBLIC_FILES");
                out.writeObject(fileOwner);
                String fileList = (String) in.readObject();
                System.out.println(fileList);
                
                System.out.print("Enter filename to download: ");
                fileName = scanner.nextLine();
                
            } else {
                System.out.println("Invalid option!");
                return;
            }
            
            // Send download request
            out.writeObject("DOWNLOAD_FILE");
            out.writeObject(fileOwner);
            out.writeObject(fileName);
            
            // Wait for server response
            String response = (String) in.readObject();
            
            if(response.startsWith("DOWNLOAD_START")) {
                // Extract file size
                String[] parts = response.split(":");
                long fileSize = Long.parseLong(parts[1]);
                
                System.out.println("Starting download: " + fileName + " (" + fileSize + " bytes)");
                
                // Create downloads directory if it doesn't exist
                File downloadDir = new File("downloads");
                if(!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }
                
                // Save file
                File outputFile = new File(downloadDir, fileName);
                FileOutputStream fos = new FileOutputStream(outputFile);
                
                long totalReceived = 0;
                int chunkNumber = 1;
                
                // Receive chunks
                while(true) {
                    String messageType = (String) in.readObject();
                    
                    if(messageType.equals("FILE_CHUNK")) {
                        int chunkSize = in.readInt();
                        byte[] chunkData = (byte[]) in.readObject();
                        
                        fos.write(chunkData);
                        totalReceived += chunkSize;
                        
                        System.out.println("Received chunk " + chunkNumber + " (" + chunkSize + " bytes) - Total: " + totalReceived + "/" + fileSize);
                        chunkNumber++;
                        
                    } else if(messageType.equals("DOWNLOAD_COMPLETE")) {
                        fos.close();
                        System.out.println("Download complete! File saved to: " + outputFile.getAbsolutePath());
                        break;
                    }
                }
                
            } else {
                System.out.println("Server: " + response);
            }
            
        } catch (Exception e) {
            System.out.println("Download failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String username = "";
        
        try {
            Socket socket = new Socket("localhost", 6666);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // Read prompt from server
            String msg = (String) in.readObject();
            System.out.print(msg + " ");  // "Enter your username:"
   
            // Send username
            username = scanner.nextLine();
            out.writeObject(username);

            // Read welcome message
            String response = (String) in.readObject();
            System.out.println(response);

            // Menu loop
            while (true) {
                System.out.println("\n=== Menu ===");
                System.out.println("1. List all clients");
                System.out.println("2. List my files");
                System.out.println("3. Upload file");
                System.out.println("4. List public files of a user");
                System.out.println("5. Download file");
                System.out.println("6. View my upload/download history");
                System.out.println("7. Make a file request");
                System.out.println("8. View my file requests");
                System.out.println("9. View unread messages");
                System.out.println("10. Log out");
                System.out.print("Choose option: ");

                String choice = scanner.nextLine();

                if(choice.equals("1")) {
                    out.writeObject("LIST_USERS");
                    String result = (String) in.readObject();
                    System.out.println(result);
                }
                else if(choice.equals("2")) {
                    out.writeObject("LIST_MY_FILES");
                    String result = (String) in.readObject();
                    System.out.println(result);
                }
                else if(choice.equals("3")) {
                    handleFileUpload(out, in, scanner);
                }
                else if(choice.equals("4")) {
                    System.out.print("Enter username: ");
                    String targetUser = scanner.nextLine();
                    
                    out.writeObject("LIST_PUBLIC_FILES");
                    out.writeObject(targetUser);
                    
                    String result = (String) in.readObject();
                    System.out.println(result);
                }
                else if(choice.equals("5")) {
                    handleFileDownload(out, in, scanner, username);
                }
                else if(choice.equals("6")) {
                    out.writeObject("VIEW_HISTORY");
                    String result = (String) in.readObject();
                    System.out.println(result);
                }
                else if(choice.equals("7")) {
                    // Make file request
                    System.out.print("Enter file description: ");
                    String description = scanner.nextLine();
                    
                    System.out.print("Enter recipient (username or 'ALL' for broadcast): ");
                    String recipient = scanner.nextLine();
                    
                    out.writeObject("MAKE_REQUEST");
                    out.writeObject(description);
                    out.writeObject(recipient);
                    
                    String result = (String) in.readObject();
                    System.out.println(result);
                }
                else if(choice.equals("8")) {
                    // View file requests received
                    out.writeObject("VIEW_REQUESTS");
                    String result = (String) in.readObject();
                    System.out.println(result);
                }
                else if(choice.equals("9")) {
                    // View unread messages
                    out.writeObject("VIEW_MESSAGES");
                    String result = (String) in.readObject();
                    System.out.println(result);
                }
                else if(choice.equals("10")) {
                    out.writeObject("LOG_OUT");
                    String result = (String) in.readObject();
                    System.out.println(result);
                    break;
                }
                else {
                    System.out.println("Invalid option");
                }
            }
            
            socket.close();
            scanner.close();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}