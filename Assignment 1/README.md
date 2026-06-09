# Assignment 1: Multi-Client File Server (Socket Programming)



A multithreaded client-server file sharing system built from scratch in Java using raw TCP sockets. Supports concurrent clients, chunked file transfer with acknowledgment, public/private file access control, file requests, and per-user upload/download logging.

---

## Features

- **Multi-client support** — each client handled by a dedicated `Worker` thread
- **Unique login enforcement** — duplicate username connections are rejected
- **Chunked file upload** — server negotiates a random chunk size (`MIN_CHUNK_SIZE` to `MAX_CHUNK_SIZE`); client splits file and sends sequentially with ACK per chunk
- **Buffer-aware uploads** — server enforces a `MAX_BUFFER_SIZE` across all in-progress uploads
- **Public / private files** — uploader controls visibility; only public files are browsable by others
- **File download** — server streams at `MAX_CHUNK_SIZE` without waiting for ACK
- **File requests** — unicast or broadcast (`ALL`) requests with auto-generated request IDs
- **Notifications** — server pushes messages to requesting clients when their requested file is uploaded
- **Upload/download log** — per-user log file with filename, timestamp, action, and status
- **Graceful offline handling** — incomplete uploads discarded if client disconnects mid-transfer

---

## File Structure

```
Assignment 1/
├── Client.java          # Client-side logic — login, upload, download, requests
├── Server.java          # Main server — accepts connections, spawns Worker threads
├── Worker.java          # Per-client thread — handles all client commands
├── FileMetadata.java    # Stores file info (name, size, access type, chunks)
├── FileRequest.java     # Models a file request (description, recipient, requestID)
└── Message.java         # Models a server-to-client notification message
```

> All files use `package Threading;` — compile from the **parent directory** of `Assignment 1`.

---

## Configuration

Defined as constants in `Server.java`:

```java
MAX_BUFFER_SIZE = 10 MB   // Max total size of chunks in upload buffer
MIN_CHUNK_SIZE  = 50 KB   // Minimum negotiated chunk size
MAX_CHUNK_SIZE  = 500 KB  // Maximum negotiated chunk size / download chunk size
```

---

## How to Run

**Step 1 — Compile** (run from the directory *containing* the `Assignment 1` folder):

```bash
javac "Assignment 1/*.java" -d .
```

This compiles all files and places the `Threading/` class output in the current directory.

**Step 2 — Start the server:**
```bash
java Threading.Server
```
Server listens on port `6666`.

**Step 3 — Start a client** (open a new terminal, same directory):
```bash
java Threading.Client
```
Enter a unique username when prompted. Open multiple terminals to simulate multiple clients.

