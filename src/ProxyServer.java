import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ProxyServer extends Thread {

    Socket Socket_Client;//Socket connect to Client
    Socket Socket_Server;//Socket connect to Server

    List<String> blacklist;//Black List block domain
    int number;//Stt of Socket
    StringBuilder message;//Store Request from Client

    BufferedReader Client_to_Proxy;
    OutputStream Proxy_to_Client;
    OutputStream Proxy_to_Server;

    ProxyServer(Socket s,List<String> blacklist,int number) {
        this.Socket_Client = s;
        this.blacklist = blacklist;
        this.number = number;
        this.message = new StringBuilder();
    }
    //Main Interface to run Proxy
    //Read Request from Client
    //Check Black List
    //Send to Server
    //Receive Response from Server
    //Send Response to Client
    @Override
    public void run() {
        try {
            Socket_Client.setKeepAlive(true);
            //Set time out of Stream
            Socket_Client.setSoTimeout(2500);
            //Create Reader to Read Request from Client
            Client_to_Proxy = new BufferedReader(new InputStreamReader(Socket_Client.getInputStream()));
            //Get OutputStream to Send Response from Server to Client
            Proxy_to_Client = Socket_Client.getOutputStream();

            //Read first line of Header Request
            String mLine = Client_to_Proxy.readLine();
            //Spilt first line to String Array
            String[] components = mLine.split(" ");

            System.out.println("No "+number+" - "+mLine);

            //Check Method is GET,POST only work with GET,POST.
            if(!is_Get_Post_Protocol(components[0])) {
                System.out.println("NOT SUPPORT.");
                Socket_Client.close();
                return;
            }
            //Create URL to host
            URL url = new URL(components[1]);
            //Get host from URL
            String host = url.getHost();
            //Check Domain has been blocked
            if (isBlockDomain(host))
                return;
            //Create Socket connect to Server at Port 80 - WebServer.
            Socket_Server = new Socket(InetAddress.getByName(host), 80);
            Socket_Server.setTcpNoDelay(true);
            Proxy_to_Server = Socket_Server.getOutputStream();
            //Set time out of Stream
            Socket_Server.setSoTimeout(6800);

            //Read Request from Client and send to Server
            Request_Client_to_Server(mLine);

            //Get request from Server 2 proxy and Write to Client
            Response_Server_to_Client();

            Socket_Server.close();
            Socket_Client.close();
        } catch (Exception e) {
            try{
                if(!Socket_Client.isClosed()) Socket_Client.close();
            }catch (IOException e1){}
            System.out.println("No "+number+"|"+e.toString());
        }
    }
    //Send 403 Request to Client
    public void blockResponse() throws IOException {
        String blocked = "HTTP/1.1 403 Forbidden\r\n\r\n" +
                "<!doctype html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>403 - Forbidden</title>\n\n" +
                "    <meta charset=\"utf-8\" />\n" +
                "    <meta http-equiv=\"Content-type\" content=\"text/html; charset=utf-8\" />\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\n" +
                "    <style type=\"text/css\">\n" +
                "    body {\n" +
                "        background-color: #b7fbc4;\n" +
                "        margin: 0;\n" +
                "        padding: 0;\n" +
                "        font-family: \"Open Sans\", \"Helvetica Neue\", Helvetica, Arial, sans-serif;\n" +
                "        \n" +
                "    }\n" +
                "    div {\n" +
                "        width: 650px;\n" +
                "        margin: 6em auto;\n" +
                "        padding: 20px;\n" +
                "        background-color: #85ddf0;\n" +
                "        border-radius: 1em;\n" +
                "    }\n" +
                "    a:link, a:visited {\n" +
                "        color: #38488f;\n" +
                "        text-decoration: none;\n" +
                "    }\n" +
                "    @media (max-width: 700px) {\n" +
                "        body {\n" +
                "            background-color: #fff;\n" +
                "        }\n" +
                "        div {\n" +
                "            width: auto;\n" +
                "            margin: 0 auto;\n" +
                "            border-radius: 0;\n" +
                "            padding: 1em;\n" +
                "        }\n" +
                "    }\n" +
                "    </style>    \n" +
                "</head>\n\n" +
                "<body>\n" +
                "<div>\n" +
                "    <h2 style=\"text-align:center;\">Welcome to Thong's Proxy</h1>" +
                "    <h1 style=\"font-size:80px;text-align:center;color:FireBrick;\">403</h1>\n" +
                "    <h1 style=\"font-size:20px;text-align:center;\">FORBIDDEN</h1>" +
                "    <p style=\"text-align:center;\">This domain has been blocked.</p>" +
                "</div>\n" +
                "</body>\n" +
                "</html>\n";
        Proxy_to_Client.write(blocked.getBytes());
    }
    //Check if Domain has been block
    //Input: Host
    //Output: True and Send 403 to Client if domain has been block else False.
    public boolean isBlockDomain(String host) throws IOException {
        for (String s : blacklist) {
            if (host.equals(s)) {//Compare
                blockResponse();
                Proxy_to_Client.flush();//flush OutStream
                Socket_Client.close();//Close Socket
                System.out.println("--------------------------------------------");
                return true;
            }
        }
        return false;
    }
    //Check protocol is GET or POST request
    //Input: Request
    //Output: True if(GET,POST),False if others.
    private static boolean is_Get_Post_Protocol(String Request){
        return Request.equals("GET") || Request.equals("POST");
    }
    //Read Request and Send Request to Server
    //Input:first String of Header
    public void Request_Client_to_Server(String mLine) throws IOException{
        message.append(mLine+"\r\n");

        while (Client_to_Proxy.ready()) {//InputStream is available
            mLine = Client_to_Proxy.readLine();//Read Data
            if (mLine.equals(""))
                message.append("\r\n");
            else
                message.append(mLine+"\r\n");
        }
        message.append("\r\n");

        Proxy_to_Server.write(message.toString().getBytes());//Send Data to Server
        Proxy_to_Server.flush();//flush OutStream
    }
    //Receive Data from Server and Send to Client
    public void Response_Server_to_Client () throws IOException{
        Socket_Server.getInputStream().transferTo(Proxy_to_Client);//Transfer all data(bytes) from Server to Client
        Proxy_to_Client.flush();//flush OutStream
    }
    public static void main(String[] args){
        try {
            //Create New Socket Server
            ServerSocket ss = new ServerSocket(8888);
            //Create array store Black List
            List<String> blacklist = new ArrayList();
            //Create InputStream to file
            InputStream readFile = new FileInputStream("blacklist.conf");
            //Create Reader to read data from file
            BufferedReader bufferReadFile = new BufferedReader(new InputStreamReader(readFile));
            String Buffer;
            //Read Data from file
            while ((Buffer = bufferReadFile.readLine())!= null) {
                blacklist.add(Buffer);
            }
            bufferReadFile.close();
            readFile.close();

            int count = 0;
            //Multi process proxy
            while (true) {
                System.out.println("No " + count++ + "...Waiting for client on port " + ss.getLocalPort());
                Socket s = ss.accept();//Listen and accept connection from client
                new ProxyServer(s,blacklist,count).start();//Run Proxy
            }
        }catch (Exception e){
            System.out.println(e.toString());
        }
    }
}