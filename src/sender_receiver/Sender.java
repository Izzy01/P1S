package sender_receiver;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Sender
 * public class Sender extends JFrame implements ActionListener
 * The Sender class represents a server.
 * @author Ilnaz Daghighian
 */
public class Sender extends JFrame implements ActionListener{
	
	private static final long serialVersionUID = 1L;
	private JTextField filePathField = new JTextField(); 
	private JTextField sizeOfPacketField = new JTextField(); 
	private JTextField timeoutField = new JTextField(); 
	private JTextField windowSizeField = new JTextField(); 
	private JTextField corrputField = new JTextField(); 
	private JTextField receiverIPAddressField = new JTextField(); 
	private JTextField receiverPortField = new JTextField(); 
	private JButton sendButton = new JButton("Send");
	private JTextArea displayArea = new JTextArea(); 

	private String filePath; 
	private int sizeOfPacket;
	private int timeout;
	private int windowSize;
	private double disrupt;
	private String receiverIPAddress;
	private int receiverPort;
	
	private DatagramSocket sendSocket; //socket to connect to server
	private DatagramPacket sendPacket; //packet being sent to receiver
	private DatagramPacket ackPacket; //packet being prepared to receive ACK
	
	private List<byte[]> packetsWithProtocol; 
	private List<Integer> listOfPacketsToDisrupt;
	private int index = 0;
	private int numOfPackets;
	
	private int acknoSent; 
	private int ackExpected = 1; 
	private boolean drop; 
	private boolean corruptData; 
	

	public static void main( String[] args ) {
	     Sender sender = new Sender(); 
	     sender.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	} 
	
	//set up GUI 
	public Sender() {
		    
	   super( "SENDER" );
	   setSize( 600, 800 ); 
	   setVisible( true ); 
      
      	/* LAYOUT */
		setLayout(new BorderLayout());
		
		/* TOP PANEL */
		JPanel topPanel = new JPanel(new GridLayout(2, 8));
		JLabel addressOfFileLabel = new JLabel("File path:");
		JLabel sizeOfPacketLabel = new JLabel("P Size:");
		JLabel timeOutLabel = new JLabel("Timeout:");
		JLabel windowSizeLabel = new JLabel("W Size:");
		JLabel dataCorrupLabel = new JLabel("% Disrupt:");
		JLabel receiverIPAddressLabel = new JLabel("Rec IPaddr:");
		JLabel receiverPortLabel = new JLabel("Rec Port:");
		
		topPanel.add(addressOfFileLabel);
		topPanel.add(sizeOfPacketLabel);
		topPanel.add(timeOutLabel);
		topPanel.add(windowSizeLabel);
		topPanel.add(dataCorrupLabel);
		topPanel.add(receiverIPAddressLabel);
		topPanel.add(receiverPortLabel);
		topPanel.add(new JLabel());

		topPanel.add(filePathField);
		topPanel.add(sizeOfPacketField);
		topPanel.add(timeoutField);
		topPanel.add(windowSizeField);
		topPanel.add(corrputField);
		topPanel.add(receiverIPAddressField);
		topPanel.add(receiverPortField);
		topPanel.add(sendButton);
		sendButton.addActionListener(this); 
		add(topPanel, BorderLayout.NORTH);

		/* BOTTOM PANEL */ 
		displayArea.setFont(new Font("Sans-Serif", Font.PLAIN, 16));
		displayArea.setLineWrap(true);
		displayArea.setWrapStyleWord(true);
		JScrollPane scrollPane = new JScrollPane(displayArea);
	    add(scrollPane, BorderLayout.CENTER);

	  }//end constructor
	   
	   @Override
	   public void actionPerformed(ActionEvent event) {
   			//get user input and set values- then run 
   			getData();	
   			if (getData()) {
   				run();
   			}
       } 
	   
	   public void run() {
		   try{
			   	////creating packets from user input////          
	      		byte[] bFile = readBytesFromFile(filePath);// convert file to byte[] 
	      		
	      		//splitting byte[] bFile into given size of packet 
	      		List<byte[]> listOfByteArrays = splitBytesArray(bFile, (sizeOfPacket-12));
	      		//adding protocol bytes 
	  			packetsWithProtocol = byteArrayWithProtocol(listOfByteArrays);
	  			numOfPackets = packetsWithProtocol.size(); //total number of packets 
	  			
	  			//generating a sorted list of packets to disrupt
	  			listOfPacketsToDisrupt = generateRandomArray(disrupt, numOfPackets); 
		  
	  		  //create DatagramSocket for sending and receiving packets
		      try {
		         sendSocket = new DatagramSocket();
		         sendSocket.connect(InetAddress.getByName(receiverIPAddress), receiverPort);
		         sendSocket.setSoTimeout(timeout);//block for no more than timeout value given
		      } 
		      catch ( SocketException | UnknownHostException  socketException ) {
		         System.exit( 1 );
		      } 
 			
  	  			int startByteOffset = 0;
  	  			int endByteOffset = -1;
  	  			String message = "";
  	  			
  	  			/// while there are packets to send do the following ///  	  			
  	  			do {
  					//sets boolean values to true if packet should be dropped, corrupted or delayed
  	  				dropCorruptOrDelay(listOfPacketsToDisrupt); 
  	  				
  	  				//if there are still packets get them ready to send -- if bad ACK/no ACK re-send same packet 
  					if (index < numOfPackets) {
  	      				byte[] data = packetsWithProtocol.get(index);//get next packet
  	      				sendPacket = new DatagramPacket(data, data.length, InetAddress.getByName(receiverIPAddress), 
  	      									receiverPort);//create packet 
  	      				
  	      				//data needed to display information 
  	      				startByteOffset = (endByteOffset + 1);
  	      				endByteOffset = (startByteOffset + data.length);
  	      				message = "["+ startByteOffset + " : " + endByteOffset + "]";
  	      				
	  	      				if(drop || corruptData){
			  	      				if(drop) {
			  	      					displayMessage( "\n[SENDing]: " + "[ " + index + " ] " +  message +
			  	      							" [ " + System.currentTimeMillis() + " ]" + " [ DROP ]\n");
			  	      					System.out.println("\n[SENDing] : " + "[ " + index + " ] " + message + 
			  	      							" [ " + System.currentTimeMillis() + " ]" + " [ DROP ]\n"); 		
			  	      					drop = false; 
			  	      				}
			  	      				if(corruptData) {	
			  	      					byte[] corrupt = {5,5,5,5,5,5,5,5,5};
			  	      					DatagramPacket corruptPacket = new DatagramPacket(corrupt, corrupt.length, 
			  	      							InetAddress.getByName(receiverIPAddress), receiverPort);
			  	      					sendSocket.send(corruptPacket);
			  	      					displayMessage("\n[SENDing]: " + "[ " + index + " ] " + " [ CORRUPTDATAPACKET ]" + 
			  	      							" [" + System.currentTimeMillis() + "]" + " [ ERRR ]\n");
			  	      					System.out.println("\n[SENDing]: " + "[ " + index + " ] " + " [ CORRUPTDATAPACKET ]" + 
			  	      							" [" + System.currentTimeMillis() + "]" + " [ ERRR ]\n");
			  	      					corruptData = false;
			  	      				}	
	  	      				}
	  	      				else {
	  	      					displayMessage("\n[SENDing]: " + "[" + index + "] " + message + 
	  	      							" [" + System.currentTimeMillis() + "]" + " [SENT]\n");
	  	      					System.out.println("\n[SENDing]: " + "[" + index + "] " +  message + 
	  	      							" [" + System.currentTimeMillis() + "]" + " [SENT]\n");
	  	      					sendSocket.send(sendPacket); 
	  	     				}
  	  				}
  					//no more packets to send break while loop 
	  	  			else if (index == numOfPackets) {
	  	  				break;  
	  	  			}
	  	  				
  	  				/////wait for expected ACK Packet to arrive from Receiver or Timeout ///////

  	  				byte[] ackData = new byte[8];//set up receiving packet
  	  				ackPacket = new DatagramPacket(ackData, ackData.length );
  	  				
  	  				//packet is sent wait for a good ACK packet to be sent else keep re-sending the same packet		
  	  				boolean receivedResponse = false; 
  	  				
  	  				do {
  	  					try {
  	  						sendSocket.receive(ackPacket);//wait for packet	  	  					
  	  					} 
  	  					catch (SocketTimeoutException ex) {//time out and packet will be resent
				        	   displayMessage("\n[TimeOut]: " + "[" + index + "]"  + "\n");
				        	   System.out.println("\n[TimeOut]: " + "[" + index + "]"  + "\n");
  	  					}
  	  					
  	  					// ACK was not sent from receiver/dropped
  	  					if(!containsData(ackData)) { 
  	  						displayMessage("\n[ReSend]: " + "[" + index + "] " + message + 
  	  								" [" + System.currentTimeMillis() + "]" + "[SENT]\n");
  	  						System.out.println("\n[ReSend]: " + "[" + index + "] "  + message +  
  	  								" [" + System.currentTimeMillis() + "]" + "[SENT]\n");
  	  						sendSocket.send(sendPacket);
  	  					}
  	  					//if an ACK is sent
  	  					else { 
  	  						ackData = ackPacket.getData(); //byte[] of ACK packet sent by receiver

			            	boolean goodACK = decodeAckPacket(ackData); //decode ackPacket 
			            	if (goodACK){
			            		if(ackExpected == acknoSent) {
			            			ackExpected = (ackExpected == 0) ? 1 : 0; //reset ackExpected for next ACK packet 
			            			if (index == (numOfPackets-1)){
			            				displayMessage("\n[AckRcvd For]: " + "[" + index + "] [LAST PACKET SENT]\n");
				            			System.out.println("\n[AckRcvd For]: " + "[" + index + "]" + "[LAST PACKET SENT]\n");
				            			index++; 
			            				receivedResponse = true; 
			            			}
			            			else {
			            				displayMessage("\n[AckRcvd For]: " + "[" + index + "] [MoveWnd]\n");
			            				System.out.println("\n[AckRcvd For]: " + "[" + index + "] [MoveWnd]\n");
			            				index++; 
			            				receivedResponse = true; 
			            			}
			            		} 
			            		else {
			            			displayMessage("\n[AckRcvd For]: " + "[" + (index) + "]" + " [DuplAck]\n");
			            			System.out.println("\n[AckRcvd For]: " + "[" + (index) + "]" + " [DuplAck]\n");
			            			displayMessage("\n[ReSend]: " + "[" + index + "]" +  message + 
			            					"[" + System.currentTimeMillis() + "]" + " [SENT]\n");
			            			System.out.println("\n[ReSend]: " + "[" + index + "]" + " [" + message + "] " + 
			            					" [" + System.currentTimeMillis() + "]" + " [SENT]\n");
			            			sendSocket.send(sendPacket);
			            		}
			            	}
			            	else {
			            		displayMessage("\n[AckRcvd For]: " + "[" + index + "]" + " [ErrAck]\n");
			            		displayMessage("\n[ReSend]: " + "[" + index + "]" + " [" + message + "] " + 
			            				" [" + System.currentTimeMillis() + "]" + " [SENT]\n");
			            		System.out.println("\n[AckRcvd For]: " + "[" + index + "]" + "[ErrAck]\n");
			            		System.out.println("\n[ReSend]: " + "[" + index + "]" + " [" + message + "] " + 
			            				" [" + System.currentTimeMillis() + "]" + " [SENT]\n");
			            		sendSocket.send(sendPacket);
			            	}	
  	  					} 	
  	  					
  	  					}while(!receivedResponse); //loop until a good ACK is received
  	  				
  	  					receivedResponse = false; //reset receivedResponse to false 

  	  			} while (index < numOfPackets ); //end outer do while loop -all packets sent
  	  			
		   }catch(Exception e){}
		   finally{
			   try{
				   sendSocket.disconnect();
				   sendSocket.close();
			   }
			   catch(Exception e){}
			   }
  	  			
	  }//end run()  

	   
	   //////////PRIVATE HELPER METHODS//////////

	   //captures text field data and converts accordingly and sets defaults if needed 
	 	private boolean getData() {
	 			
 			filePath = filePathField.getText().replace("\\", "\\\\");
 			filePath = (filePath.equals("")) ? "c:\\temp\\E.txt" : filePath;//for debugging 
 	
	 		String sizeOfPacketStr = sizeOfPacketField.getText();
	 		sizeOfPacket = Integer.parseInt(sizeOfPacketStr); //packet size (total with protocol bytes)

	 		String timeoutStr = timeoutField.getText();
	 		timeout = Integer.parseInt(timeoutStr);

	 		String windowSizeStr = windowSizeField.getText();
	 		windowSize = Integer.parseInt(windowSizeStr);
	 		windowSize = (windowSize == 1) ? windowSize : 1;//window size fixed at one
 	
	 		String corruptStr = corrputField.getText();
	 		disrupt = Double.parseDouble(corruptStr);
 		
 			receiverIPAddress = receiverIPAddressField.getText();
 			receiverIPAddress = (receiverIPAddress.equals(null)) ? "localhost" : receiverIPAddress;//default
 			
	 		String receiverPortStr = receiverPortField.getText();
	 		receiverPort = Integer.parseInt(receiverPortStr);

 			return true;
 		}

		//method to convert file to byte[]
		private byte[] readBytesFromFile(String filePath) {

	        FileInputStream fileInputStream = null;
	        byte[] bytesArray = null;

	        try {
	            File file = new File(filePath);
	            bytesArray = new byte[(int) file.length()];

	            //read file into bytes[]
	            fileInputStream = new FileInputStream(file);
	            fileInputStream.read(bytesArray);

	        } catch (IOException e) {
	            e.printStackTrace();
	        } finally {
	            if (fileInputStream != null) {
	                try {
	                    fileInputStream.close();
	                } catch (IOException e) {
	                    e.printStackTrace();
	                }
	            }
	        }
	        return bytesArray;
		}
		 
	 	//converting byte[] into List<byte[]> of specified size + and empty byte[] at the end 	
		private List<byte[]> splitBytesArray(byte[] originalArray, int sectionSize) {
			
			 List<byte[]> listOfByteArrays = new ArrayList<byte[]>();
			 int totalSize = originalArray.length;
			 if(totalSize < sectionSize ){
			    sectionSize = totalSize;
			 }
			 int from = 0;
			 int to = sectionSize;
			 
			 while(from < totalSize){
			     byte[] partArray = Arrays.copyOfRange(originalArray, from, to);
			     listOfByteArrays.add(partArray);

			     from += sectionSize;
			     to = from + sectionSize;
			     if(to>totalSize){
			         to = totalSize;
			     }
			 }		 
			 byte[] endPacket = new byte[0]; 
			 listOfByteArrays.add(endPacket);//append empty packet to end stream 
			 return listOfByteArrays;
		}

		//method to create a list<byte[]> that contains protocol as well as data
		private List<byte[]> byteArrayWithProtocol(List<byte[]> originalArray) {
			
			List<byte[]> listOfByteArrays = new ArrayList<byte[]>();
				
			for (int i = 1; i <= originalArray.size(); i++){
				int ackNumber;
				int sequenceNumber;
				short checksum = checksum(originalArray.get(i-1));//calculate checksum 	
				byte[] checksumArray = shortToBytes(checksum);//convert checksum into byte[]
				
				//when even seq = 1 ack = 0 --- when odd seq = 0 ack = 1
				if(i%2 == 0){
					ackNumber = 0; 
					sequenceNumber = 1;
				}
				else {
					ackNumber = 1;
					sequenceNumber = 0;
				}
				
				byte[] data = originalArray.get(i-1);//byte[] of data
				short length = (short) (12 + data.length);//length of byte[] variable for last packet 	
				byte[] lengthArray = shortToBytes(length);//convert length into byte[]
				byte[] ackNumberArray = intToBytes(ackNumber);//convert ackNumber into byte[]
				byte[] sequenceNumberArray = intToBytes(sequenceNumber);//convert squenceNumber into byte[]
				//byte[] of protocol and data	
				byte[] protocalAndData = concat(checksumArray, lengthArray, ackNumberArray, sequenceNumberArray, data);
				listOfByteArrays.add(protocalAndData);		
			}
			return listOfByteArrays; 
		}

   		//Method to decode ACK packet  
		private boolean decodeAckPacket(byte[] data){
			
			byte[] checksumSent = Arrays.copyOfRange(data, 0, 2);
			byte[] lengthSent = Arrays.copyOfRange(data, 2, 4);
			byte[] ackNumberSent = Arrays.copyOfRange(data, 4, 8);
			
			byte[] checksumData = concat(lengthSent, ackNumberSent);
			
			//check received values against sent values 
			short checksumValueSent = bytesToShort(checksumSent);
			short checksumValueReceived = checksum(checksumData); 
			short lengthValueSent = bytesToShort(lengthSent);
			short lengthReceived = (short) data.length;
										
			if (checksumValueSent != checksumValueReceived){
				return false;  //corrupt ACK 
			}				
			if (lengthValueSent != lengthReceived){
				return false;  //corrupt ACK 
			}	
			acknoSent = bytesToInt(ackNumberSent);			
			return true; 
		}

		/////METHODS USING ByteBuffer FOR CONVERTING PRIMATIVE VALUES TO BYTE[] AND VICE VERSA  -BIG ENDIAN/////
		
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
			
		//method to concatenate byte[]'s 
		private byte[] concat(byte[]...arrays) {
		    // Determine the length of the result array
		    int totalLength = 0;
		    for (int i = 0; i < arrays.length; i++) {
		        totalLength += arrays[i].length;
		    }

		    // create the result array
		    byte[] result = new byte[totalLength];

		    // copy the source arrays into the result array
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
	 	
	 	//generate a list of random Integers sorted in ascending order that represents the number of packets to disrupt
	 	private List<Integer> generateRandomArray(double percent, int outOfTotal){
	 	   
	 		double packets = (double)outOfTotal * percent; 
	 		int totalPacketsToCorrupt = (int) Math.round(packets);
	 		
	 		List<Integer> list = new ArrayList<Integer>(totalPacketsToCorrupt);
	 	    Random randomGenerator = new Random();
	 	    while (list.size() < totalPacketsToCorrupt) {

	 	      int random = randomGenerator.nextInt(outOfTotal);
	 	      if (!list.contains(random)) {
	 	          list.add(random);
	 	      }
	 	  }
	 	   Collections.sort(list);
	 	   return list;
	 	}  
	 	
	 	//compares disrupt list to current index of packet -if equal will randomly either drop or corrupt
	 	private void dropCorruptOrDelay(List<Integer> list){
	 		
			for(int i = 0; i<list.size(); i++) {
			    if(list.get(i).intValue() == index) {
			    	listOfPacketsToDisrupt.remove(i);
			    	Random randomGenerator = new Random();
			    	int random = randomGenerator.nextInt(2); 
			    	if(random == 0){
			    		drop = true;
			    	}
			    	if(random == 1 && index != 0){
			    		corruptData = true;   
			    	}
			    	break;
			    }
			}
		}
	 	
	   //need to figure out swing multi-threading to have displayArea update in real-time
	   private void displayMessage(final String messageToDisplay) {
	      SwingUtilities.invokeLater(
	         new Runnable() {
	            public void run() { 
	               displayArea.append(messageToDisplay); // display message
	            } 
	         } 
	      ); 
	   } // end method displayMessage
 	
	 	//checking to see if an ACK Packet received contains any data returns true if byte[] is not all 0's
	 	private boolean containsData(byte[] data){		
	 		
	 		for (byte b : data) {
	 		    if (b != 0) {
	 		        return true;
	 		    }
	 		}
	 		return false;
	 	} 	
			 	
}//end class 
