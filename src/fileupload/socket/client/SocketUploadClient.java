package fileupload.socket.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;


public class SocketUploadClient {
	
	private String serverAddress = "localhost"; // for testing set to localhost
	private int serverPort = 81;
	private String filePath = null;
	private Socket socket = null;
	private byte[] boundary = null;
	
	public static byte SEMI_COLON = 59;
	public static byte CR = 13;
	public static byte LF = 10;
	
	public static byte[] END_HEADER = new byte[]{SEMI_COLON, CR, LF, CR, LF};
	public static byte[] EOF = new byte[]{CR, LF, CR, LF};
	
	Random rnd = new Random();
	
	private static int BOUNDARY_LENGTH = 24;

    public static void main(String args[]) throws Exception{

        SocketUploadClient client = new SocketUploadClient();
        client.testSocket();
        //client.execute();
    }
    
    public void execute() throws Exception {
    	String address = readServerAddress();
    	if(address != null && address.trim().length() == 0) {
    		serverAddress = address; 
    	}
    	int port = readServerPort();
    	if(port != -1) {
    		serverPort = port;
    	}
    	filePath = readFilePath();
    	socket = connect(serverAddress, serverPort);
        
        File f = new File(filePath);
        
        sendFilename(socket, f.getName());
        boundary = generateBoundary();
        sendBoundary(socket,boundary);
        sendFile(socket, f);
        processResponse(socket);
        socket.close();
    }
    
    public String readServerAddress() {
        /*Console console = System.console();
        console.printf("Please enter server address :");
        String server = console.readLine();*/
    	String server = readString("Please enter server address :");
        return server;
    }
    
    public int readServerPort() {
        /*Console console = System.console();
        console.printf("Please enter server port(81 by default) :");
        String portS = console.readLine();
        int port = 81;
        if(portS != null && portS.trim().length() > 0) {
            port = Integer.parseInt(portS);
        }*/
    	int port = readInt("Please enter server port(81 by default) :");
        return port;
    }
    
    public String readFilePath() {
        /*Console console = System.console();
        console.printf("Please enter file (Give full path if client is not executed from same folder) :");
        String f = console.readLine();*/
    	String f = readString("Please enter file (Give full path if client is not executed from same folder) :");
        return f;
    }
    
    protected String readString(String message) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		PrintWriter writer = new PrintWriter(new PrintWriter(System.out),true);
		String input = null;
		
		try {
			writer.printf(message);
			input = reader.readLine();
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalArgumentException(e);
		}
		
		return input;
	}
	
	protected int readInt(String message) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		PrintWriter writer = new PrintWriter(new PrintWriter(System.out),true);
		int input = -1;
		
		try {
			writer.printf(message);
			String inputS = reader.readLine();
			if(inputS != null && inputS.trim().length() > 0) {
				input = Integer.parseInt(inputS);
	        }
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalArgumentException(e);
		}
		
		return input;
	}
    
    public void closeSocket(Socket skt) throws Exception {
        skt.close();
    }
    
    public void processResponse(Socket skt) throws Exception{

        BufferedReader breader = new BufferedReader(new InputStreamReader(skt.getInputStream()));
        String line = null;

        while((line = breader.readLine()) != null) {
            System.out.println("in 3");
            System.out.println(line);
        }
        breader.close();
    }
    
    public void sendFile(Socket skt, File f) throws Exception {
        OutputStream os = skt.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f));
        int c = 0;
        byte[] buff = new byte[4096];
        
        while((c = bis.read(buff)) != -1 ) {
            bos.write(buff,0,c);
        }
        // Write eof so that do not wait.
        bos.write(EOF);
        bos.flush();
        //bos.close();
        //os.close();
        bis.close();
        System.out.println("Completed sending file " + f.getName());
    }
    
    public void sendFilename(Socket skt, String filename) throws Exception {
        OutputStream os = skt.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        bos.write(filename.getBytes());
        bos.write(END_HEADER);
        bos.flush();
        System.out.println("Sent file name " + filename);
    }
    
    public void sendBoundary(Socket skt, byte[] bnd) throws Exception {
    	OutputStream os = skt.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        bos.write(bnd);
        bos.write(END_HEADER);
        bos.flush();
        System.out.println("Sent boundary.");
    }
    
    public void testSocket() throws Exception {
    	serverAddress = "localhost";
    	serverPort = 81;
    	socket = connect(serverAddress, serverPort);
        OutputStream os = socket.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        bos.write(new String("Test.txt;\r\n\r\n123456789012345678901234;\r\n\r\nContentContentContentContent123456789012345678901234").getBytes());
        bos.close();
        os.close();
        System.out.println("Test complete");
    }
    
    public Socket connect(String server,int port) throws Exception {
        
        Socket skt = new Socket(server, port);
        
        return skt;
    }
    
    protected byte[] generateBoundary() {
    	
    	byte[] ia = new byte[BOUNDARY_LENGTH];
		for(int i=0; i<ia.length; i++) {
			int a = rnd.nextInt(9);
			ia[i] = (byte)a;
		}
		
		for(int i=0; i<ia.length; i++) {
			System.out.print(ia[i]);
		}
		System.out.println();
		
		return ia;
    }
    
/*
jar cfe client.jar socket.SocketUploadClient socket/*.class
*/

}

