package fileupload.socket.server;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;

public class SocketUploadServer {

	private ServerSocket serverSocket = null;
	private String savePath = System.getProperty("user.home") + "\\dropbox";
	private int serverPortNumber = 81;
	private CountDownLatch startLatch = null;
	private CountDownLatch shutdownLatch = null;
	
	public static byte SEMI_COLON = 59;
	public static byte CR = 13;
	public static byte LF = 10;
	
	public static byte[] END_HEADER = new byte[]{SEMI_COLON, CR, LF, CR, LF};
	public static byte[] EOF = new byte[]{CR, LF, CR, LF};
	
	private static int BOUNDARY_LENGTH = 24;
	
	private byte[] buff = null;
	
	private static int BUFFER_SIZE = 4098;
	
	public static void main(String[] args) {
		SocketUploadServer server = new SocketUploadServer();
		server.init();
	}
	
	public void init() {

		try {
			
			startLatch = new CountDownLatch(1);
			shutdownLatch = new CountDownLatch(1);
			
			serverSocket = new ServerSocket(serverPortNumber);

			Thread th = listen(serverSocket);
			startLatch.await();
			
			reconfigureServer(th);
			
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}

	}
	
	public void reconfigureServer(Thread th) throws Exception {
		
		System.out.println("Server running at port " + serverPortNumber + 
				", save path = " + savePath);
		System.out.println("To reconfigure, please enter values at below prompt.");

		int port = readServerPort();
		if(port != -1) {
			serverPortNumber = port;
		}

		if(serverSocket.getLocalPort() != serverPortNumber) {
			System.out.println("in 1");
			th.interrupt();
			System.out.println("in 2");
			serverSocket.close();
			System.out.println("in 3");
			shutdownLatch.await();
			System.out.println("in 4");
			serverSocket = new ServerSocket(serverPortNumber);
			System.out.println("in 5");
			th = listen(serverSocket);
			System.out.println("in 6");
			startLatch = new CountDownLatch(1);
			shutdownLatch = new CountDownLatch(1);
			System.out.println("Reconfigured server to use port : " + serverPortNumber);
		}
		
		String newSavePath = readSavePath();
		System.out.println("in 101");
		if(!savePath.equals(newSavePath)) {
			savePath = newSavePath;
			System.out.println("Reconfigured server to use save path : " + savePath);
		}
	}
	
	protected String readSavePath() {
		// Console don't work in Eclipse
        /*Console console = System.console();
        console.printf("Please enter path where files will be saved (Users home directory by default) :");
        String path = console.readLine();*/
		String path = readString("Please enter path where files will be saved (Leave blank to use users home directory) :");
		if(path == null || path.trim().length() == 0) {
			path = System.getProperty("user.home");
		}
        return path;
    }
	
	protected int readServerPort() {
		// Console don't work in Eclipse
        /*Console console = System.console();
        console.printf("Please enter server port(81 by default) :");
        String portS = console.readLine();
        int port = -1;
        if(portS != null && portS.trim().length() > 0) {
            port = Integer.parseInt(portS);
        }*/
		int port = readInt("Please enter server port(81 by default) :");
        return port;
    }
	
	protected String readString(String message) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		PrintWriter writer = new PrintWriter(new PrintWriter(System.out),true);
		String input = null;
		
		try {
			writer.printf(message);
			input = readWhenAvailable(reader);
		} catch (IOException e) {
			e.printStackTrace(System.out);
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
			String inputS = readWhenAvailable(reader);
			if(inputS != null && inputS.trim().length() > 0) {
				input = Integer.parseInt(inputS);
	        }
			
		} catch (IOException e) {
			e.printStackTrace(System.out);
			throw new IllegalArgumentException(e);
		}
		
		return input;
	}
	
	// this is workaround fix for http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4809647
	public String readWhenAvailable(BufferedReader reader) throws IOException{
		String input = null;
		while(true) {
			int av = System.in.available();
			if(av == 0) {
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {}
				continue;
			}
			input = reader.readLine();
			break;
		}
		
		return input;
	}
	
	
	public Thread listen(final ServerSocket serverSocket) throws Exception {

		// Use a thread to listen as ServerSocket.accept() blocks current
		// thread.
		Thread th = new Thread(new Runnable() {
			Socket skt = null;
			
			public void run() {
				ServerSocket myServer = serverSocket;
				System.out.println("Waiting for connection...................");
				startLatch.countDown();
				
				try {
					skt = serverSocket.accept();
					InetSocketAddress remoteAddr = (InetSocketAddress) skt
							.getRemoteSocketAddress();
					System.out.println();
					System.out.println("Received connection from "
							+ remoteAddr.getAddress().getHostAddress());
					process(skt);
					
				} catch (SocketException se) {
					if(myServer.isClosed()) {
						System.out.println("Server is shutdown........ by socket close");
					} else {
						System.out.println("Server is shutdown.....");
					}
					shutdownLatch.countDown();
				} catch (Exception e) {
					e.printStackTrace(System.out);
					throw new IllegalStateException(e);
				}
			}

		});
		th.start();
		return th;
	}
	
	public void process(Socket skt) throws Exception {
		
		//System.out.println("in...........");

	    String fname = null;
	    byte[] boundary = null;
	    
	    InputStream is = skt.getInputStream();
	    
	    BufferedInputStream bis = new BufferedInputStream(is);
	    buff = new byte[BUFFER_SIZE];
	    readFromStream(BUFFER_SIZE, bis, 0);
	    
	    if(buff == null) {
	    	throw new IllegalStateException("Client did not send any data.");
	    }
	    System.out.println("buffer size " + buff.length);
	    // first read till file name part is over. file name ends with \r\n
	    byte[] fnameBytes = readHeader(bis);
	    if(fnameBytes != null && fnameBytes.length > 0) {
	    	fname = new String(fnameBytes);
	    } else {
	    	throw new IllegalStateException("File name not found....");
	    }
	    
	    System.out.println("File name ..........." + fname);
	    
	    boundary = readHeader(bis);
	    
	    sop("Received boundary length " + boundary.length);
	    
	    System.out.println("Boundary..........." + new String(boundary));
	    
	    System.out.println("buff ... " + new String(buff));
	    
	    File f = new File(savePath + "\\" + fname);

	    if(f.exists()) {
	        sendResponse(skt, "This file is not uploaded as file already exists with name " + fname);
	        System.out.println("This file is not uploaded as file already exists with name " + fname );
	        listen(serverSocket);
	        return;
	    }
	    
	    // read content of file and write it to file.
	    
	    FileOutputStream fos = new FileOutputStream(f);
	    
	    byte[] body = null;
	    while((body = readTillBoundary(bis, boundary)).length > 0) {
	    	sop("Writing to file " + new String(body));
	    	fos.write(body);
	    	fos.flush();
	    }
 
	    fos.close();
	    System.out.println("Saved file " + fname);
	    sendResponse(skt, "File saved in server.");
	    listen(serverSocket);
	}
	
	protected void readFromStream(int size, InputStream bis, int destPos) throws IOException {
		sop("in readfromstream - size - " + size + " , destpos - " + destPos);
		int c = 0;
		byte[] oldbuff = new byte[buff.length];
		int currentusedbuffsize = 0;
		if(destPos > 0) {
			currentusedbuffsize = destPos;
		}
		System.out.println("Used buff size " + currentusedbuffsize);
		System.arraycopy(buff, 0, oldbuff, 0, buff.length);
	    byte[] temp = new byte[size];
	    
	    if(bis.available() > 0 && (c = bis.read(temp)) != -1 ) {
	    	System.out.println("Read bytes count " + c);
	    	System.out.println("Read bytes value " + new String(temp));
	    	buff = new byte[currentusedbuffsize + c];
	    	System.arraycopy(oldbuff, 0, buff, 0, currentusedbuffsize);
	    	System.arraycopy(temp, 0, buff, destPos, c);
	    } else if(destPos > 0){
	    	System.out.println("Nothing read from input. Resizing array - old size " + buff.length
	    			+ ", new size " + currentusedbuffsize);
	    	// resize existing buff
	    	buff = new byte[currentusedbuffsize];
	    	System.arraycopy(oldbuff, 0, buff, 0, currentusedbuffsize);
	    }
	    
	    System.out.println("buffer bytes count " + buff.length);
	}
	
	protected int findInArray(byte[] buff, byte[] find) {
		
		if(buff.length < find.length){
			return -1;
		}
		
		int pos = 0;
		int firstpos = -1;
		for(int i = 0; i < find.length; i++) {
			pos = find(buff, find[i], pos);
			if(pos > -1) {
				firstpos = firstpos == -1 ? pos : firstpos; 
				//pos = find(buff, find[i], pos);
				pos++;
			} else {
				pos = -1;
				break;
			}
		}
		return pos == -1 ? pos : firstpos;
	}
	
	protected int find(byte[] buff, byte find, int start) {
		int pos = -1;
		for(int i = start; i < buff.length; i++) {
			if(buff[i] == find) {
				return i;
			}
		}
		return pos;
	}
	
	public byte[] readTillBoundary(InputStream is, byte[] boundary) throws IOException {
		sop("in read body. buff before reading from stream " + new String(buff));
		int pos = -1;
		byte[] body = new byte[0];
		// read till buff size is boundary length + 1
		int prevbuffsize = -1;
		int minbufffillsize = BUFFER_SIZE > BOUNDARY_LENGTH * 2 + 1 ? BUFFER_SIZE : BOUNDARY_LENGTH * 2 + 1;
		while(buff.length > prevbuffsize && buff.length < minbufffillsize) {
			sop("reading since buff is not full to check boundary overlap buff size " 
					+ buff.length + " , prevbuffsize - " + prevbuffsize );
			prevbuffsize = buff.length;
			readFromStream(minbufffillsize, is, buff.length);
		}
		
		sop("in read body buff size " + buff.length);
		if((pos = findInArray(buff, boundary)) > -1) {
			System.out.println("Found boundary at " + pos + " in " + new String(buff));
			sop("copy size " + (buff.length - boundary.length));
			body = new byte[pos];
			System.arraycopy(buff, 0, body, 0, buff.length - boundary.length);
			buff = new byte[0];
		} else if(buff.length > 0){
			sop("Boundary not found in " + new String(buff));
			// boundary segment can be there so copy BUFFER_SIZE bytes only 
			body = new byte[buff.length - boundary.length];
			System.arraycopy(buff, 0, body, 0, buff.length - boundary.length);
			byte[] temp = new byte[buff.length - body.length];
			System.arraycopy(buff, buff.length - boundary.length, temp, 0, boundary.length);
			buff = new byte[boundary.length];
			System.arraycopy(temp, 0, buff, 0, temp.length);
			//buff = new byte[0];
			
		}
		sop("body size " + body.length);
		sop("body  " + new String(body));
		
		return body;
	}
	
	public byte[] readHeader(InputStream is) throws IOException {
		sop("in read header. buff before reading from stream " + buff.length);
		// it is assumed that header is at beginning of stream. ie starts at byte position 0.
		int pos = -1;
		byte[] header = null;
		
		while(pos == -1) {
			if((pos = findInArray(buff, END_HEADER)) > -1) {
				System.out.println("header end pos " + pos);
	
				// copy header to header array
				header = new byte[pos];
				System.arraycopy(buff, 0, header, 0, pos);
				// clear header from buff so that next header can be read.
				int remaininguffsize = buff.length - (pos + END_HEADER.length);
				if(remaininguffsize > 0) {
					System.out.println("buffer is not empty, refilling with " + (buff.length - remaininguffsize));
					byte[] temp = new byte[remaininguffsize];
					System.arraycopy(buff, pos + END_HEADER.length, temp, 0, remaininguffsize);
					System.arraycopy(temp, 0, buff, 0, temp.length);
					readFromStream(buff.length - remaininguffsize, is, remaininguffsize);
				} else {
					System.out.println("buffer is empty, refilling");
					readFromStream(BUFFER_SIZE, is, 0);
				}
	
				System.out.println("header - " + new String(header));
			} else {
				sop("did not find header in buff " + new String(buff));
				sop("reading form stream size " + (buff.length + BUFFER_SIZE));
				readFromStream(buff.length + BUFFER_SIZE, is, buff.length);
				sop("after reading from stream buff " + new String(buff));
			}
		}
		return header;
	}
	
	public void sendResponse(Socket skt, String msg) throws Exception {
	    PrintWriter writer = new PrintWriter(new OutputStreamWriter(skt.getOutputStream()));
	    writer.print(msg);
	    writer.flush();
	    writer.close();
	}
	
	public static void sop(String m) {
		System.out.println(m);
	}

}
