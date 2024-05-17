import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MyWebServer {
    // Server name
    // Arbitrarily chosen; change at will
    private static final String SERVER_NAME = "Charles Hu's Web Server";

    // Program entry point
    public static void main(String argv[]) throws Exception{
        // Connection variables
        int serverPort = Integer.parseInt(argv[0]);
        String clientSentenceMain;
        String clientSentenceIfModified;
        // Parser variables
        String[] clientSentenceTokenized;
        String[] pathTokenized;
        SimpleDateFormat httpDateFormat = new SimpleDateFormat("EEE MMM d hh:mm:ss zzz yyyy");
        // Handler variables
        File resource = null;
        boolean hasFile;
        boolean hasErrorMsg;
        // HTTP field variables
        // Used to denote correct behavior for each request
        String method;
        String path = null;
        String dateString;
        Date ifModifiedDate = null;

        //====================
        // REQUEST CONNECTION
        //====================

        // Create a server socket to accept incoming request at specified port
        ServerSocket welcomeSocket = new ServerSocket(serverPort);

        // Print start up status
        System.out.println(SERVER_NAME + " started on port " + serverPort + "\n");

        // Main connection loop; connects and services each client
        while (true) {
            // Wait on client arrival, welcome when contacted
            Socket connectionSocket = welcomeSocket.accept();

            // Set up I/O streams attached to socket to communicate with client
            BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
            DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());

            // Pull first line from client request
            // We know that this will always include request method and path
            if ((clientSentenceMain = inFromClient.readLine()) != null) {
                // Read the rest of the request lines
                // Search if If-Modified-Since date was specified
                boolean hasIfModified = false;
                do {
                    clientSentenceIfModified = inFromClient.readLine();

                    // Null type guard
                    if (clientSentenceIfModified == null) {
                        break;
                    }

                    // Loop through entire client message until If-Modified-Since date is found
                    // Otherwise, if end of messaged reached then leave flag false and ignore this header
                    if (clientSentenceIfModified.contains("If-Modified-Since")) {
                        hasIfModified = true;
                        break;
                    }
                } while (clientSentenceIfModified.length() != 0);

                //================
                // REQUEST PARSER
                //================

                // Tokenize the HTTP request line for parsing
                clientSentenceTokenized = clientSentenceMain.split(" ");

                // Check if given HTTP method is usable
                // Deny everything except HEAD or GET
                method = clientSentenceTokenized[0];
                int errorCode = 0;
                if (!method.equals("HEAD") && !method.equals("GET")) {
                    errorCode = 501;
                } else {
                    // Perform a check on the path to see if it refers to a directory or a file
                    // Build base URI to check
                    path = argv[1] + clientSentenceTokenized[1];
                    // Find final token in path
                    pathTokenized = path.split("/");
                    // Check if it is file (includes "." character) or directory
                    if (!pathTokenized[pathTokenized.length - 1].contains(".")) {
                        // Add reference to index.html in URI if directory
                        path += "/index.html";
                    }

                    // If If-Modified-Since date exists, parse and format for HttpRequest object use
                    if (hasIfModified) {
                        dateString = clientSentenceIfModified.replace("If-Modified-Since: ", "");
                        try {
                            ifModifiedDate = httpDateFormat.parse(dateString);
                        } catch (Exception e) {
                            // If given date is not formattable, throw request error
                            errorCode = 400;
                        }
                    }
                }

                // HTTP response container
                String responseMessage = "";
                // Flag for if response has to also send file with it
                hasFile = false;

                // If parsing brought up an error, report it and skip response handling
                hasErrorMsg = false;
                if (errorCode > 0) {
                    hasErrorMsg = true;
                    switch (errorCode) {
                        case 400:
                            responseMessage = "HTTP/1.1 400 Bad Request\r\nContent-Length: 71\r\n";
                            System.out.println("Servicing 400");
                            break;
                        case 501:
                            // Preface message with \r\n to address bug of unknown cause where 501 will not appear without it
                            responseMessage = "\r\nHTTP/1.1 501 Not Implemented\r\nContent-Length: 75\r\n";
                            System.out.println("Servicing 501");
                            break;
                    }
                } else {
                    //=================
                    // REQUEST HANDLER
                    //=================

                    // Search for file in given path
                    resource = new File(path);

                    // Calculate current date for server (local time)
                    String currentDate = httpDateFormat.format(new Date());

                    // Prepare date and server headers for response message
                    String dateMsg = "Date: " + currentDate + "\r\n";
                    String serverMsg = "Server: " + SERVER_NAME + "\r\n";

                    // Check if requested file exists
                    if (resource.exists()) {
                        // If so, prepare last modified date and content length headers for response message
                        String lastModifiedDate = httpDateFormat.format(resource.lastModified());
                        String lastModifiedMsg = "Last-Modified: " + lastModifiedDate + "\r\n";
                        String contentLengthMsg = "Content-Length: " + resource.length() + "\r\n";

                        // If request has if modified date, check if given date if later than file date
                        if (hasIfModified && ifModifiedDate.after(httpDateFormat.parse(lastModifiedDate))) {
                            // If so, throw 304 since file is up to date
                            responseMessage = "HTTP/1.1 304 Not Modified\r\n" + dateMsg + "Content-Length: 72\r\n";
                            errorCode = 304;
                            hasErrorMsg = true;
                            System.out.println("Servicing 304");
                        } else {
                            // Else prepare HEAD or GET response
                            switch (method) {
                                case "HEAD":
                                    responseMessage = "HTTP/1.1 200 OK\r\n" + dateMsg + serverMsg + lastModifiedMsg + contentLengthMsg;
                                    System.out.println("Servicing 200 HEAD");
                                    break;
                                case "GET":
                                    responseMessage = "HTTP/1.1 200 OK\r\n" + dateMsg + serverMsg + lastModifiedMsg + contentLengthMsg;
                                    System.out.println("Servicing 200 GET");
                                    // Flag that server needs to send file alongside header
                                    hasFile = true;
                                    break;
                            }
                        }
                    } else {
                        // If not, throw 404 error
                        responseMessage = "HTTP/1.1 404 Not Found\r\n" + dateMsg + serverMsg + "Content-Length: 69\r\n";
                        errorCode = 404;
                        hasErrorMsg = true;
                        System.out.println("Servicing 404");
                    }
                }
                
                // Report message status
                if (resource != null) {
                    System.out.println("Requested resource: " + resource.toString());
                }
                System.out.println("Sent message:");
                System.out.println(responseMessage);

                // Send response header
                outToClient.write((responseMessage + "\r\n").getBytes());

                // Send file along with header if flagged
                if (hasFile) {
                    Files.copy(resource.toPath(), connectionSocket.getOutputStream());
                }

                // If error code has been sent, also send error message in body
                if (hasErrorMsg) {
                    switch (errorCode) {
                        case 304:
                            outToClient.write("<!DOCTYPE html><html><head></head><body>304 Not Modified</body></html>\r\n".getBytes());
                            break;
                        case 400:
                            outToClient.write("<!DOCTYPE html><html><head></head><body>400 Bad Request</body></html>\r\n".getBytes());
                            break;
                        case 404:
                            outToClient.write("<!DOCTYPE html><html><head></head><body>404 Not Found</body></html>\r\n".getBytes());
                            break;
                        case 501:
                            outToClient.write("<!DOCTYPE html><html><head></head><body>501 Not Implemented</body></html>\r\n".getBytes());
                            break;
                    }
                }
            }

            // Close connection with client
            connectionSocket.close();
        }
    }
}
