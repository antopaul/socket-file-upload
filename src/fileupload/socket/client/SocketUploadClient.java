/*
 * Copyright 2013 Anto Paul
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fileupload.socket.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Random;


public class SocketUploadClient {
	
	private String serverAddress = "0.0.0.0"; // for testing set to localhost
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
	
	public static String OK = "ok";
	
	public static String TYPE_FILE = "Type : File";
	public static String TYPE_DIRECTORY = "Type : Directory";
	
	private static int BUFFER_SIZE = 1024 * 64;
	
	
	
	Random rnd = new Random();
	
	private static int BOUNDARY_LENGTH = 24;
	
	private int errorCount = 0;
	
	private long respTime = 0l;

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
    	if(address != null && address.trim().length() != 0) {
    		serverAddress = address; 
    	}
    	
    	long startTime;
    	long endTime;

    	int port = readServerPort();
    	if(port != -1) {
    		serverPort = port;
    	}
    	File f = null;
    	while(true) {
	    	filePath = readFilePath();
	    	if(filePath == null || "".equals(filePath.trim())) {
	    		System.out.println("Please enter valid file name.");
	        	continue;
	    	}
	    	f = new File(filePath);
	    	if(!f.exists()) {
	        	System.out.println("File does not exist " + filePath + ". Please recheck filename.");
	        	continue;
	        }
	    	break;
    	}
    	if(f.isDirectory()) {
    		String recursive = readString("You entered a directory path. " +
        			"Do you want to upload all files recursively from that directory?(Y/n) : ");
    		if(recursive == null || "".equals(recursive)) {
    			recursive = "y";
    		}
        	if("y".equalsIgnoreCase(recursive)) {
        		startTime = System.currentTimeMillis();
        		recursiveSendFile();
        	} else {
        		return;
        	}
    	} else {
    		startTime = System.currentTimeMillis();
    		sendFile();
    	}
    	endTime = System.currentTimeMillis();
    	closeSocket(socket);
    	
    	System.out.println("Total time to send files " + (endTime - startTime) / 1000 + "s");
    	System.out.println("Time for processing response " + (respTime/1000) + "s");
    	System.out.println("Total errors : " + errorCount);
    }
    
    public void recursiveSendFile() throws Exception {
    	File f = new File(filePath);
    	
    	if(basePath == null) {
    		basePath = filePath;
    		//sop("basepath - " + basePath);
    	}
    	
        if(!f.exists()) {
        	sop("File does not exist " + filePath + ". Please recheck filename.");
        	return;
        }
        
        // If directory, send all files from that folder recursively.
        if(f.isDirectory()) {
	        File[] files = f.listFiles();
	        if(files.length == 0) {
	        	sop("The given directory don't have any files in it - " + f.getName());
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
        	sop("File does not exist " + filePath + ". Please recheck filename.");
        	return;
        }
        if(socket == null) {
        	socket = connect(serverAddress, serverPort);
        }
    	sendSingle(f);
    	//closeSocket(socket);
    }
    
    public void sendSingle(File f) throws Exception {

        sendFilename(socket, f);
        // check response to see if file already exists.
        if(checkFileExistsInServer(socket)) {
        	return;
        }
        if(f.isDirectory()) {
        	String resp = processResponse(socket);
        	sop(resp);
        	//closeSocket(socket);
        	return;
        }
        
        boundary = generateBoundary();
        sendBoundary(socket,boundary);
        sendFile(socket, f, boundary);
        String resp = processResponse(socket);
        sop(resp);
       
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
    	long start = System.currentTimeMillis();
    	boolean foundEndHeader = false;
    	//System.out.println("Waiting for response");
    	
    	StringBuilder sb = new StringBuilder();
    	
        InputStream bis = skt.getInputStream();
        //byte[] buff = new byte[1];
        int c = -1;
        // responses will be small so read that to memory.
        while((c = bis.read()) != -1) {
        	//sb.append(new String(Arrays.copyOf(buff, c)));
        	sb.append((char)c);
        	if(sb.toString().endsWith(new String(END_HEADER))) {
        		//sop("Found header " + sb);
        		// Strip END_HEADER from response.
        		sb.replace(sb.length() -5, sb.length(), "");
        		foundEndHeader = true;
        		break;
        	}
        }
        if(!foundEndHeader) {
        	sop("1 header not found in reponse " + sb);
        	if(sb.toString().endsWith(new String(END_HEADER))) {
        		// Strip END_HEADER from response.
        		sb.replace(sb.length() -5, sb.length(), "");
        		foundEndHeader = true;
        		System.exit(1);
        	}
        	
        }
        if(!foundEndHeader) {
        	sop("2 header not found in reponse " + sb);
        	System.exit(1);
        }
        respTime += System.currentTimeMillis() - start; 
        return sb.toString();
    }
    
    public boolean checkFileExistsInServer(Socket socket) throws Exception {
    	boolean fileexists = false;
    	// check response to see if file already exists.
        String resp = processResponse(socket);
        if(!OK.equals(resp)) {
        	sop("Error sending file - " + resp);
        	System.exit(1);
        	fileexists = true;
        }
    	return fileexists;
    }
    
    public void sendFile(Socket skt, File f, byte[] bnd)  throws IOException {
    	
    	try {
	        OutputStream os = skt.getOutputStream();
	        BufferedOutputStream bos = new BufferedOutputStream(os, BUFFER_SIZE);
	        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f), BUFFER_SIZE);
	        int c = 0;
	        byte[] buff = new byte[BUFFER_SIZE];
	        
	        while((c = bis.read(buff)) != -1 ) {
	            bos.write(buff,0,c);
	            bos.flush();
	        }
	        // Write boundary to end sending file.
	        bos.write(bnd);
	        //bos.write(new String("123456789012345678901234").getBytes());
	        bos.flush();
	        bis.close();
	        sop("Completed sending file " + f.getName());
    	} catch(IOException ioe) {
    		errorCount++;
    		sop("Error sending file " + f.getName());
    		ioe.printStackTrace();
    		throw ioe;
    	}
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
	        	//sop("Sending base path " + p);
	        	filename = p;
        	}
        }
        bos.write(filename.getBytes());
        bos.write(END_HEADER);
        bos.flush();
        //System.out.println("Sent file name " + filename);
    }
    
    public void sendBoundary(Socket skt, byte[] bnd) throws Exception {
    	OutputStream os = skt.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
        bos.write(bnd);
        //bos.write(new String("123456789012345678901234").getBytes());
        bos.write(END_HEADER);
        bos.flush();
        //System.out.println("Sent boundary.");
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
    	String content = "ContentContentContentContent";
    	String boundary = "123456789012345678901234";
    	socket = connect(serverAddress, serverPort);
    	OutputStream os = socket.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
    	for(int i=minfilelength; i<= maxfilelength; i++) {
	    	
	    	String fname = new String(testGenerateString("a", i));
	    	testSaveFile(fname, "c:/sockettest", content);
	        
	        bos.write(TYPE_FILE.getBytes());
	        bos.write(END_HEADER);
	        bos.write(fname.getBytes());
	        bos.write(END_HEADER);
	        bos.flush();
	        if(checkFileExistsInServer(socket)) {
	        	continue;
	        }
	        bos.write(boundary.getBytes());
	        bos.write(END_HEADER);
	        bos.flush();
	        bos.write(new String(content + boundary).getBytes());
	        bos.flush();
	        String resp = processResponse(socket);
	        System.out.println(resp);
	        System.out.println("Test complete - " + i);
    	}
    	bos.close();
        os.close();
        socket.close();
        System.out.println("Full test complete............");
    }
    
    public void testSocketVaryFileContentLength() throws Exception {
    	serverAddress = "localhost"; 
    	serverPort = 8001;
    	int minfilelength = 256;
    	int maxfilelength = 1024;
    	String content = null;
    	String boundary = "123456789012345678901234";
    	socket = connect(serverAddress, serverPort);
    	OutputStream os = socket.getOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream(os);
    	for(int i=minfilelength; i<= maxfilelength; i++) {
	    	
	    	String fname = new String(""+i);
	    	content = testGenerateString("a", i);
	    	testSaveFile(fname, "c:/sockettest", content);
	        
	        bos.write(TYPE_FILE.getBytes());
	        bos.write(END_HEADER);
	        bos.write(fname.getBytes());
	        bos.write(END_HEADER);
	        bos.flush();
	        sop("Send type and file name");
	        if(checkFileExistsInServer(socket)) {
	        	continue;
	        }
	        bos.write(boundary.getBytes());
	        bos.write(END_HEADER);
	        bos.flush();
	        bos.write(new String(content + boundary).getBytes());
	        bos.flush();
	        sop("send content");
	        String resp = processResponse(socket);
	        System.out.println(resp);
	        System.out.println("Test complete - " + i);
    	}
    	bos.close();
        os.close();
        socket.close();
        System.out.println("Full test complete............");
    }
    
    public void testSocketVaryFileContentLength1() throws Exception {
    	serverAddress = "localhost";
    	serverPort = 8001;
    	int minfilelength = 1;
    	int maxfilelength = 32;
    	socket = connect(serverAddress, serverPort);
    	OutputStream os = socket.getOutputStream();
    	ByteArrayOutputStream bos = null;
    	for(int i=minfilelength; i<= maxfilelength; i++) {
    		
	    	
	        
	        //BufferedOutputStream bos = new BufferedOutputStream(os);
	        bos = new ByteArrayOutputStream();
	        bos.write(new String(i 
	        		+ ";\r\n\r\n111111111111111111111111;\r\n\r\n" + 
	        		testGenerateString("a", i)+ "111111111111111111111111").getBytes());
	        
	        
	        testSaveFile(i+"", "c:/sockettest", testGenerateString("a", i));
	        System.out.println("Test complete - " + i);
    	}
    	bos.close();
    	os.close();
        socket.close();
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
        
        //Socket skt = new Socket(server, port);
    	Socket skt = new Socket();
        //skt.setSendBufferSize(BUFFER_SIZE);
        //sop("SO_SNDBUF " + skt.getSendBufferSize());
        skt.setTcpNoDelay(true);
    	SocketAddress address = new InetSocketAddress(server, port);
        skt.connect(address);
        return skt;
    }
    
    protected byte[] generateBoundary() {
    	
    	byte[] ia = new byte[BOUNDARY_LENGTH];
		for(int i=0; i<ia.length; i++) {
			int a = rnd.nextInt(9);
			ia[i] = (byte)a;
		}
		//sop("boundary - ");
		//for(int i=0; i<ia.length; i++) {
		//	System.out.print(ia[i]);
		//}
		//System.out.println();
		
		return ia;
    }
    
    public static void sop(String m) {
		System.out.println(m);
	}
    
/*
jar cfe client.jar socket.SocketUploadClient socket/*.class
*/

}

