package fileupload.socket.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.Random;


public class SocketUploadClient {
	
	private String serverAddress = "localhost"; // for testing set to localhost
	private int serverPort = 8001;
	private String filePath = null;
	private Socket socket = null;
	private byte[] boundary = null;
	private String basePath = null;
	
	public static byte SEMI_COLON = 59;
	public static byte CR = 13;
	public static byte LF = 10;
	public static byte COLON = 58;
	
	public static byte[] END_HEADER = new byte[]{SEMI_COLON, CR, LF, CR, LF};
	public static byte[] EOF = new byte[]{CR, LF, CR, LF};
	
	public static String OK = "ok";
	
	public static String TYPE_FILE = "Type : File";
	public static String TYPE_DIRECTORY = "Type : Directory";
	
	Random rnd = new Random();
	
	private static int BOUNDARY_LENGTH = 24;

    public static void main(String args[]) throws Exception{
        SocketUploadClient client = new SocketUploadClient();
        //client.testSocketFilesRecursively();
        //client.testSocketFilesFromFolder();
        //client.testSocketVaryFileContentLength();
        //client.testSocketVaryFileNameLength();
        client.execute();
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
    	File f = new File(filePath);
    	if(!f.exists()) {
        	System.out.println("File does not exist " + filePath + ". Please recheck filename.");
        	return;
        }
    	if(f.isDirectory()) {
    		String recursive = readString("You entered a directory path. " +
        			"Do you want to upload all files recursively from that directory?(y/n) : ");
        	if("y".equalsIgnoreCase(recursive)) {
        			recursiveSendFile();
        	} else {
        		return;
        	}
    	} else {
    		sendFile();
    	}
    }
    
    public void recursiveSendFile() throws Exception {
    	File f = new File(filePath);
    	
    	if(basePath == null) {
    		basePath = filePath;
    		sop("basepath - " + basePath);
    	}
    	
        if(!f.exists()) {
        	System.out.println("File does not exist " + filePath + ". Please recheck filename.");
        	return;
        }
        
        // If directory, send all files from that folder recursively.
        if(f.isDirectory()) {
	        File[] files = f.listFiles();
	        if(files.length == 0) {
	        	sop("The given directory don't have any files int it - " + f.getName());
	        }
	    	for(int i = 0; i<files.length; i++) {
	    		filePath = files[i].getAbsolutePath();
	    		sendFile();
	    		if(files[i].isDirectory()) {
	    			recursiveSendFile(); 
	    		}
	    	}
        } else {
        	sendFile();
        }
        
    }
    
    public void sendFile() throws Exception {
    	
    	File f = new File(filePath);
    	
        if(!f.exists()) {
        	System.out.println("File does not exist " + filePath + ". Please recheck filename.");
        	return;
        }
    	socket = connect(serverAddress, serverPort);
    	sendSingle(f);
    	closeSocket(socket);
    }
    
    public void sendSingle(File f) throws Exception {

        sendFilename(socket, f);
        // check response to see if file already exists.
        if(checkFileExistsInServer(socket, f)) {
        	return;
        }
        if(f.isDirectory()) {
        	String resp = processResponse(socket);
        	sop(resp);
        	closeSocket(socket);
        	return;
        }
        
        boundary = generateBoundary();
        sendBoundary(socket,boundary);
        sendFile(socket, f, boundary);
        String resp = processResponse(socket);
        System.out.println(resp);
       
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
        console.printf("Please enter server port(8001 by default) :");
        String portS = console.readLine();
        int port = 8001;
        if(portS != null && portS.trim().length() > 0) {
            port = Integer.parseInt(portS);
        }*/
    	int port = readInt("Please enter server port(8001 by default) :");
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
    
    public String processResponse(Socket skt) throws Exception{
    	System.out.println("Waiting for response");
    	
    	StringBuilder sb = new StringBuilder();
    	
        BufferedInputStream bis = new BufferedInputStream(skt.getInputStream());
        byte[] buff = new byte[1024];
        int c = -1;
        
        // responses will be small so read that to memory.
        while((c = bis.read(buff)) != -1) {
        	sb.append(new String(Arrays.copyOf(buff, c)));
        	//sop(sb.toString());
        	if(sb.toString().endsWith(new String(END_HEADER))) {
        		// Strip END_HEADER from response.
        		sb.replace(sb.length() -5, sb.length(), "");
        		break;
        	}
        }
        
        return sb.toString();
    }
    
    public boolean checkFileExistsInServer(Socket socket, File file) throws Exception {
    	boolean fileexists = false;
    	// check response to see if file already exists.
        String resp = processResponse(socket);
        if(!OK.equals(resp)) {
        	System.out.println("Error sending file. " + resp);
        	fileexists = true;
        }
    	return fileexists;
    }
    
    public void sendFile(Socket skt, File f, byte[] bnd) throws Exception {
        OutputStream os = skt.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f));
        int c = 0;
        byte[] buff = new byte[4096];
        
        while((c = bis.read(buff)) != -1 ) {
            bos.write(buff,0,c);
        }
        // Write boundary to end sending file.
        bos.write(bnd);
        //bos.write(new String("123456789012345678901234").getBytes());
        bos.flush();
        bis.close();
        System.out.println("Completed sending file " + f.getName());
    }
    
    public void sendFilename(Socket skt, File file) throws Exception {
    	String filename = file.getName();
        OutputStream os = skt.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        if(file.isFile()) {
        	bos.write(TYPE_FILE.getBytes());
        } else {
        	bos.write(TYPE_DIRECTORY.getBytes());
        }
        bos.write(END_HEADER);
        if(basePath != null) {
        	String apath = file.getAbsolutePath();
        	String p = apath.substring(apath.indexOf(basePath) + basePath.length() + 1);
        	if(!p.equals(filename)) {
	        	sop("Sending base path " + p);
	        	filename = p;
        	}
        }
        bos.write(filename.getBytes());
        bos.write(END_HEADER);
        bos.flush();
        System.out.println("Sent file name " + filename);
    }
    
    public void sendBoundary(Socket skt, byte[] bnd) throws Exception {
    	OutputStream os = skt.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        bos.write(bnd);
        //bos.write(new String("123456789012345678901234").getBytes());
        bos.write(END_HEADER);
        bos.flush();
        System.out.println("Sent boundary.");
    }
    
    public void testSocket() throws Exception {
    	serverAddress = "localhost";
    	serverPort = 8001;
    	socket = connect(serverAddress, serverPort);
        OutputStream os = socket.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        bos.write(new String("Test.txt;\r\n\r\n123456789012345678901234;\r\n\r\nContentContentContentContent123456789012345678901234").getBytes());
        bos.close();
        os.close();
        System.out.println("Test complete");
    }
    
    public void testSocketVaryFileNameLength() throws Exception {
    	serverAddress = "localhost";
    	serverPort = 8001;
    	int minfilelength = 1;
    	int maxfilelength = 255;
    	for(int i=minfilelength; i<= maxfilelength; i++) {
	    	socket = connect(serverAddress, serverPort);
	        OutputStream os = socket.getOutputStream();
	        BufferedOutputStream bos = new BufferedOutputStream(os);
	        bos.write(new String(testGenerateString("a", i) + ";\r\n\r\n123456789012345678901234;\r\n\r\nContentContentContentContent123456789012345678901234").getBytes());
	        bos.close();
	        os.close();
	        socket.close();
	        System.out.println("Test complete - " + i);
    	}
    }
    
    public void testSocketVaryFileContentLength() throws Exception {
    	serverAddress = "localhost";
    	serverPort = 8001;
    	int minfilelength = 1;
    	int maxfilelength = 32;
    	for(int i=minfilelength; i<= maxfilelength; i++) {
    		
	    	socket = connect(serverAddress, serverPort);
	        OutputStream os = socket.getOutputStream();
	        BufferedOutputStream bos = new BufferedOutputStream(os);
	        bos.write(new String(i 
	        		+ ";\r\n\r\n111111111111111111111111;\r\n\r\n" + 
	        		testGenerateString("a", i)+ "111111111111111111111111").getBytes());
	        bos.close();
	        os.close();
	        socket.close();
	        testSaveFile(i+"", "c:/sockettest", testGenerateString("a", i));
	        System.out.println("Test complete - " + i);
    	}
    }
    
    public void testSocketFilesRecursively() throws Exception {
    	serverAddress = "localhost";
    	serverPort = 8001;
    	
    	String path = "c:/sockettest/";
    	File file = new File(path);
    	filePath = file.getAbsolutePath();
    	recursiveSendFile();
    }
    
    public void testSocketFilesFromFolder() throws Exception {
    	serverAddress = "localhost";
    	serverPort = 8001;
    	
    	String path = "c:/sockettest";
    	File file = new File(path);
    	File[] files = file.listFiles();
    	for(int i = 0; i<files.length; i++) {
    		if(files[i].isDirectory()) {
    			continue;
    		}
    		filePath = files[i].getAbsolutePath();
    		sendFile();
    	}
    }
    
    public String testGenerateString(String v, int len) {
    	StringBuilder sb = new StringBuilder();
    	for(int i=0; i<len; i++) {
    		sb.append(v);
    	}
    	
    	return sb.toString();
    }
    
    public void testSaveFile(String fname, String path, String content) throws Exception {
    	File f = new File(path + "/" + fname);
    	FileWriter fw = new FileWriter(f);
    	fw.write(content);
    	fw.close();
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
		System.out.print("boundary - ");
		for(int i=0; i<ia.length; i++) {
			System.out.print(ia[i]);
		}
		System.out.println();
		
		return ia;
    }
    
    public static void sop(String m) {
		System.out.println(m);
	}
    
/*
jar cfe client.jar socket.SocketUploadClient socket/*.class
*/

}

