package sender_receiver;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

/**
 * Receiver
 * public class Receiver extends JFrame implements ActionListener
 * The Receiver class represents a client.
 * @author Ilnaz Daghighian
 */
public class Receiver extends JFrame implements ActionListener {
	
	private static final long serialVersionUID = 1L;
	private static final String UPLOAD_FOLDER = "C:\\temp\\";
	private JTextField windowSizeField = new JTextField(); 
	private JTextField corrputField = new JTextField(); 
	private JTextField receiverIPAddressField = new JTextField(); 
	private JTextField receiverPortField = new JTextField(); 
	private JButton runButton = new JButton("Run");
	private JTextArea displayArea = new JTextArea(); 
	
	private DatagramSocket receiveSocket;//socket to connect to server
	private DatagramPacket receivePacket;//packet to receive data 
	private DatagramPacket ack;
	private FileOutputStream fileOutput;
	
	private int windowSize;
	private double corrupt;
	private String receiverIPAddress;
	private int port;
 
	private byte[] outputText; 
	private int seqnoSent; 
	private int acknoSent; 
	private int seqnoExpected = 0;
	private int endOfStream;
	private int index = 0; 
	private boolean drop; 
	private boolean corruptData; 
	
	
	public static void main( String[] args ) throws InterruptedException {
	      Receiver receiver = new Receiver(); 
	   	  receiver.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	} 

	//set up GUI and DatagramSocket
	public Receiver() { 
      
	  super( "RECEIVER" );
	  setSize( 500, 800 ); 
	  setVisible( true ); 
	  
		/* LAYOUT */
		setLayout(new BorderLayout());
		
		/* TOP PANEL */
		JPanel topPanel = new JPanel(new GridLayout(2, 5));
		JLabel receiverIPAddressLabel = new JLabel("IP address: ");
		JLabel receiverPortLabel = new JLabel("Port: ");
		JLabel windowSizeLabel = new JLabel("W Size: ");
		JLabel dataCorrupLabel = new JLabel("% Disrupt:");
		
		topPanel.add(receiverIPAddressLabel);
		topPanel.add(receiverPortLabel);
		topPanel.add(windowSizeLabel);
		topPanel.add(dataCorrupLabel);
		topPanel.add(new JLabel());
		
		topPanel.add(receiverIPAddressField);
		topPanel.add(receiverPortField);
		topPanel.add(windowSizeField);
		topPanel.add(corrputField);
		topPanel.add(runButton);
		runButton.addActionListener(this);
		
		add(topPanel, BorderLayout.NORTH);

		/* BOTTOM PANEL */ 
		displayArea.setFont(new Font("Sans-Serif", Font.PLAIN, 16));
		displayArea.setLineWrap(true);
		displayArea.setWrapStyleWord(true);
		JScrollPane scrollPane = new JScrollPane(displayArea);
	    add(scrollPane, BorderLayout.CENTER);
      
   } //end constructor

   
   @Override
	public void actionPerformed(ActionEvent arg0) {
	   	//get user input and set values -then run 
		getData();	
		if(getData()){
			run();
		}
	}
   
   public void run() {
	   	//create DatagramSocket for sending and receiving packets
	      try {
	    	  InetAddress server = InetAddress.getByName(receiverIPAddress);
	    	  SocketAddress address = new InetSocketAddress(server, port );
	    	  receiveSocket = new DatagramSocket(address); 
	      } 
	      catch ( SocketException | UnknownHostException socketException ) {
	         socketException.printStackTrace();
	         System.exit( 1 );
	      }
	      
	      try {
	      //keep receiving packets until you reach last packet -then ACK and close socket 
	    	  do {
	    	  	//receive packet, display message send back ACK packet 
	        
	             byte[] data = new byte[512]; //set up packet
	             receivePacket = new DatagramPacket( data, data.length );

	             receiveSocket.receive(receivePacket); //wait to receive packet
	             
	             byte[] dataReceived = receivePacket.getData(); //get packet data 
	             
	             //decode, and if packet received is not corrupt 
	             if(decodePacket(dataReceived)){ 
	            	 
            		 byte[] ackData = ackPacket(); //create ACK packet for sender 
            		 ack = new DatagramPacket(ackData, ackData.length, receivePacket.getAddress(),
            				 receivePacket.getPort());
            		 
	             		if (seqnoSent == seqnoExpected) { //if receivedPacket is not a duplicate
	             			seqnoExpected = (seqnoExpected == 0) ? 1 : 0;  //reset seqnoExpected for next frame
		            		displayMessage("\n[RECV]: " + "[" + index + "] " + "[RECV]\n"); //packet is good
		            		System.out.println("\n[RECV]: " + "[" + index + "] " + "[RECV]\n");
		            		writeBytesToFile(outputText, UPLOAD_FOLDER + "output.txt");//write to file
		            		index++;
		            		
		            		boolean dropOrCorrupt = dropOrCorrupt(corrupt);
		            		
		            		//if last ack is corrupted or dropped re-send until a good ACK is sent to the sender
		            		if (endOfStream == 0 && dropOrCorrupt ) {
		      	    		  	endOfStream = 1;
		      	    	  	}
		            		
		            		 if (!dropOrCorrupt) {
		            			 // send packet to Sender
		            			 receiveSocket.send(ack);
		            			 displayMessage("\nACK:" + "[" + index + "]" + "[SENT]\n" );
		            			 System.out.println("\nACK:" + "[" + index + "]" + "[SENT]\n" );
		            		 }   
		            		 else {
		            			 if (drop) {
		            				 displayMessage("\nACK:" + "[" + index + "]" + "[DROP]\n" );
		            				 System.out.println("\nACK:" + "[" + index + "]" + "[DROP]\n" );
			  	      				 drop = false; 
		            			 }
		            			 if (corruptData) {
		            				 byte[] corrupt = {5,5,5,5,5,5,5,5};
		            				 displayMessage("\nACK:" + "[" + index + "]" + "[ERR]\n" );  
		            				 System.out.println("\nACK:" + "[" + index + "]" + "[ERR]\n" );
		            				 receiveSocket.send(new DatagramPacket(corrupt, corrupt.length, 
		            						 receivePacket.getAddress(), receivePacket.getPort()));
		            				 corruptData = false; 
		            			 }
		            		}
	             		} 
	             		
			            else { //receivedPacket is a duplicate
			            	displayMessage("\n[DUPL]: " + "[" + (index-1) + "] " + "[RECV]\n");
			            	displayMessage("\nACK:" + "[" + (index) + "]" + "[SENT]\n" );
			            	System.out.println("\n[DUPL]: " + "[" + (index-1) + "] " + "[RECV]\n");
			            	System.out.println("\nACK:" + "[" + (index) + "]" + "[SENT]\n" );
			            	receiveSocket.send(ack);
			            }		         	
	             }
		         else {
		            displayMessage("\n[RECV]: " + "[" + index + "] " + "[CRPT]\n");//packet is corrupt
		            displayMessage("\nACK:" + "[" + index + "]" + "[SENT]\n");  
	            	System.out.println("\n[RECV]: " + "[" + index + "] " + "[CRPT]\n");
	            	System.out.println("\nACK:" + "[" + index + "]" + "[SENT]\n");
		            receiveSocket.send(ack); 
		         }	
	             
	      } while (endOfStream != 0); //loop until endOfStream + 1 to catch drop or corrupted ACK
	     
	   }catch(Exception e){}
	      finally{
	    	  try{
	    		  fileOutput.close();
	    		  receiveSocket.disconnect();
	    		  receiveSocket.close();
	    	  }
	    	  catch(Exception e){}
	      } 
	 
   } //end run 
   	
   	   //////////////PRIVATE HELPER METHODS/////////////

	   //method to capture text field data and convert accordingly 
	   private boolean getData() {
	
			receiverIPAddress = receiverIPAddressField.getText();
			receiverIPAddress = (receiverIPAddress.equals(null)) ? "localhost" : receiverIPAddress;//default 
			
			String receiverPortStr = receiverPortField.getText();
			port = Integer.parseInt(receiverPortStr);
	
			String windowSizeStr = windowSizeField.getText();
			windowSize = Integer.parseInt(windowSizeStr);
			windowSize = (windowSize == 1) ? windowSize : 1;//window size fixed at one

			String corruptStr = corrputField.getText();
			corrupt = Double.parseDouble(corruptStr); 
			
			return true;	
		}
   
		//method to decode packet
		private boolean decodePacket(byte[] data){ 
			
			byte[] checksumSent = Arrays.copyOfRange(data, 0, 2);
			byte[] lengthSent = Arrays.copyOfRange(data, 2, 4);
			byte[] ackNumberSent = Arrays.copyOfRange(data, 4, 8);
			byte[] sequenceNumberSent = Arrays.copyOfRange(data, 8, 12);
			byte[] dataSent = Arrays.copyOfRange(data, 12, 513);
			
			//check received values against sent values 
			short checksumValueSent = bytesToShort(checksumSent);
			short checksumValueReceived = checksum(dataSent); 
			
			short lengthValueSent = bytesToShort(lengthSent); 
			short lengthReceived = (short) data.length;  
				
			if (checksumValueSent != checksumValueReceived){
				return false;  //corrupt ACK 
			}
			if (lengthValueSent != lengthReceived){
				
				//trimming byte[] dataSent of padded 0's at end 
				byte[] dataSentTrimmed = trim(dataSent);
				short trimmed = (short) (dataSentTrimmed.length + 12);
			
				if(lengthValueSent != trimmed) {
					return false;  //corrupt ACK 
				}	
			}	
			
			acknoSent = bytesToInt(ackNumberSent);
			seqnoSent = bytesToInt(sequenceNumberSent);
			endOfStream = bytesToInt(dataSent);
			outputText = dataSent;  
			return true; 
		}
   
		//trim byte[] of  zero's
		private static byte[] trim(byte[] bytes) {
		    int i = bytes.length - 1;
		    while (i >= 0 && bytes[i] == 0)
		    {
		        --i;
		    }
	
		    return Arrays.copyOf(bytes, i + 1);
		}
	   
	    //creates ackPacket byte[] for sending back to sender 
		private byte[] ackPacket(){
					
			byte[] length = shortToBytes((short) 8);
			byte[] ackNumber = intToBytes(acknoSent);
			
			byte[] data = concat(length, ackNumber);
			short checksumValue = checksum(data);
			byte[] checksum = shortToBytes(checksumValue);
			
			byte[] finalPacket = concat(checksum, length, ackNumber);
	
			return finalPacket;
			
		}

		/////METHODS USING BUFFERS FOR CONVERTING PRIMATIVE VALUES TO BYTE[] AND VICE VERSA  -BIG ENDIAN/////
	
		//convert byte[] to short 
		private short bytesToShort(byte[] bytes) {
		     return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getShort();
		}
		//convert short to byte[] 
		private byte[] shortToBytes(short value) {
		    return ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value).array();
		}
		//convert byte[] to int
		private int bytesToInt(byte [] bytes){
		    return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getInt();
		}
		//convert int to byte[]
		private  byte[] intToBytes(int value){
		    return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array();
		}
   
		//method to concatenate byte[] 
		private byte[] concat(byte[]...arrays) {
		    //Determine the length of the result array
		    int totalLength = 0;
		    for (int i = 0; i < arrays.length; i++) {
		        totalLength += arrays[i].length;
		    }

		    //create the result array
		    byte[] result = new byte[totalLength];

		    //copy the source arrays into the result array
		    int currentIndex = 0;
		    for (int i = 0; i < arrays.length; i++) {
		        System.arraycopy(arrays[i], 0, result, currentIndex, arrays[i].length);
		        currentIndex += arrays[i].length;
		    }
		    return result;
		}
   
		//checksum 
	 	private short checksum(byte[] buf) {
	 		
		 	int length = buf.length; 
		    int i = 0;
		    short sum = 0;
		    while (length > 0) {
		        sum += (buf[i++]&0xFF) << 8;
		        if ((--length)==0) break;
		        sum += (buf[i++]&0xFF);
		        --length;
		    }
			return (short) ((~((sum & 0xFFFF)+(sum >> 16)))&0xFFFF);
		}

	 	
	 	//write byte[] to .txt file 
	   private void writeBytesToFile(byte[] bFile, String fileDest) {

	         try {		 
	        	fileOutput = new FileOutputStream(fileDest, true);
	            fileOutput.write(bFile, 0, 500);
	            fileOutput.flush();
	 	     } catch (IOException e) {
	 	            e.printStackTrace();
	 	     }
	   }
	 	
	   //method that will drop or corrupt ACK packets
	 	private boolean dropOrCorrupt(double percent) {
		
 		  	Random randomGenerator = new Random();
	 	
	 	    if (randomGenerator.nextInt(100) < (percent * 100)){
		    	int random = randomGenerator.nextInt(2); 
		    	
	 	    	if(random == 0){
		    		drop = true;
		    		return true; 
		    	}
		    	if(random == 1 ){
		    		corruptData = true; 
		    		return true; 
		    	}	    
		    }
	 	    return false; 
		}
	 
	 	//need to figure out swing multi-threading to have displayArea update in real-time
	    private void displayMessage(final String messageToDisplay) {
	       SwingUtilities.invokeLater(
	          new Runnable() {
	             public void run() { 
	                displayArea.append(messageToDisplay); //display message
	             } 
	          } 
	       ); 
	    } // end method displayMessage
			
}//end class
