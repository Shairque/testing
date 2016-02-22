// SUBMISSION BY:
// --------------------------------
// Name: Lakhotia Suyash
// Matric No.: U1423096J
// Lab Group: TS1


/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class                                          *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/

import java.util.Timer;
import java.util.TimerTask;

public class SWP {
	/*========================================================================*
 	The following are provided. DO NOT CHANGE!
 	*=========================================================================*/
 	// Protocol Constants:
 	public static final int MAX_SEQ = 7;
 	public static final int NR_BUFS = (MAX_SEQ + 1)/2;

	// Protocol Variables:
 	private int oldest_frame = 0;
 	private PEvent event = new PEvent();
 	private Packet out_buf[] = new Packet[NR_BUFS];
 	private Packet in_buf[] = new Packet[NR_BUFS];

	// For Simulation Purpose Only:
 	private SWE swe = null;
 	private String sid = null;

	// Constructor:
 	public SWP(SWE sw, String s) {
        swe = sw;
        sid = s;
 	}

	// Protocol-Related Methods:
 	private void init() {
        for (int i = 0; i < NR_BUFS; i++){
            out_buf[i] = new Packet();
            in_buf[i] = new Packet();
        }
    }

    private void wait_for_event(PEvent e) {
        swe.wait_for_event(e); // may be blocked
        oldest_frame = e.seq;  // set timeout frame seq
    }

  	private void enable_network_layer(int nr_of_bufs) {
   		// Network layer is permitted to send if credit is available.
  		swe.grant_credit(nr_of_bufs);
  	}

  	private void from_network_layer(Packet p) {
  		swe.from_network_layer(p);
  	}

  	private void to_network_layer(Packet packet) {
  		swe.to_network_layer(packet);
  	}

  	private void to_physical_layer(PFrame fm) {
  		System.out.println("SWP: Sending frame: seq = " + fm.seq +
  			" ack = " + fm.ack + " kind = " +
  			PFrame.KIND[fm.kind] + " info = " + fm.info.data );
  		System.out.flush();
  		swe.to_physical_layer(fm);
  	}

  	private void from_physical_layer(PFrame fm) {
  		PFrame fm1 = swe.from_physical_layer();
  		fm.kind = fm1.kind;
  		fm.seq = fm1.seq;
  		fm.ack = fm1.ack;
  		fm.info = fm1.info;
  	}


	/*=========================================================================*
	Implement your Protocol Variables & Methods below:
	*==========================================================================*/
	private boolean no_nak = true;

    // The following method checks the circular condition of the frame numbers:
	public static boolean between(int x, int y, int z) {
		return ((x <= y) && (y < z)) || ((z < x) && (x <= y)) || ((y < z) && (z < x));
	}

    // The following method is used to construct and send a DATA, ACK or NAK frame:
    private void send_frame(int frame_type, int frame_nr, int frame_expected, Packet buffer[]) {
		PFrame s = new PFrame();  // scratch variable

       	s.kind = frame_type;  // there are 3 kinds of frames - DATA, ACK, NAK
		if (frame_type == PFrame.DATA) {
			s.info = buffer[frame_nr % NR_BUFS];
		}
		s.seq = frame_nr;  // only meaningful for data frames
		s.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);
		if (frame_type == PFrame.NAK) {
		    no_nak = false;  // one NAK per frame
		}
		to_physical_layer(s);  // transmit frame
		if (frame_type == PFrame.DATA) {
			start_timer(frame_nr);
		}
		stop_ack_timer();  // no need for separate ACK frame
    }

    public void protocol6() {
    	init();
    	int ack_expected;          // lower edge of sender's window
    	int next_frame_to_send;    // upper edge of sender's window + 1
    	int frame_expected;        // lower edge of receiver's window
    	int too_far;               // upper edge of receiver's window + 1
    	int index;                 // index of the buffer

    	boolean received[] = new boolean[NR_BUFS];  // keeps track of frames arrived

    	PFrame temp_frame = new PFrame();  // scratch variable

        // Initialize Network Layer:
    	enable_network_layer(NR_BUFS);

    	// Initialize Counter Variables:
    	ack_expected = 0;
    	next_frame_to_send = 0;
    	frame_expected = 0;
    	too_far = NR_BUFS;
    	index = 0;
    	for(int i = 0; i < NR_BUFS; i++)
    		received[i] = false;

    	while(true) {
    		wait_for_event(event);
    		switch(event.type) {
    			case (PEvent.NETWORK_LAYER_READY):  // Sending a Frame
    				from_network_layer(out_buf[next_frame_to_send % NR_BUFS]);  // fetch the new packet
    				send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf);  // transmit the frame
    				next_frame_to_send = inc(next_frame_to_send);  // advance upper edge of window
    				break;

    			case (PEvent.FRAME_ARRIVAL):  // Receiving a Frame
    				from_physical_layer(temp_frame);  // fetch incoming frame from physical layer

    				if (temp_frame.kind == PFrame.DATA) {
    					// An undamaged frame has arrived.

    					if ((temp_frame.seq != frame_expected) && no_nak) {
    						send_frame(PFrame.NAK, 0, frame_expected, out_buf);
                        } else {
                            start_ack_timer();
                    	}

                    	// Check if the frame received is between the expected frames of the sliding window & if it has not
                    	// been previously received. Allows frames to be accepted in any order of arrival.
                    	if (between(frame_expected, temp_frame.seq, too_far) && received[temp_frame.seq % NR_BUFS] == false) {
                    		received[temp_frame.seq % NR_BUFS] = true;  // mark buffer as full
                    		in_buf[temp_frame.seq % NR_BUFS] = temp_frame.info;  // insert data into buffer

                    		while (received[frame_expected % NR_BUFS]) {
                                // Pass frames from the physical layer to the network layer and advance window.

                    			to_network_layer(in_buf[frame_expected % NR_BUFS]);
                    			no_nak = true;
                    			received[frame_expected % NR_BUFS] = false;  // mark undamaged frame as received
                    			frame_expected = inc(frame_expected);  // increment lower edge of receiver's window
                    			too_far = inc(too_far);  // increment upper edge of receiver's window
                    			start_ack_timer();  // start ACK timer
                    		}
                    	}
                    }

                    // If a NAK frame arrives, check that the frame is between the expected frames of the sliding window &
                    // resend the data of the frame for which a NAK has arrived.
                    if (temp_frame.kind == PFrame.NAK && between(ack_expected, ((temp_frame.ack + 1) % (MAX_SEQ + 1)),
                        next_frame_to_send)) {
                            send_frame(PFrame.DATA, ((temp_frame.ack + 1) % (MAX_SEQ + 1)), frame_expected, out_buf);
                    }

                    while (between(ack_expected, temp_frame.ack, next_frame_to_send)) {
                        stop_timer(ack_expected % NR_BUFS);    // If a complete & undamaged frame is received,
                        ack_expected = inc(ack_expected);      // advance lower edge of sender's window &
                        enable_network_layer(1);               // free one buffer slot.
                    }

    				break;

    			case (PEvent.CKSUM_ERR):
    				if (no_nak) {
                        // Damaged frame has arrived.
                        send_frame(PFrame.NAK, 0, frame_expected, out_buf);
    				}
    				break;

    			case (PEvent.TIMEOUT):
                    // If the timer has expired for the frame, resend the frame.
                    send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf);
    				break;

    			case (PEvent.ACK_TIMEOUT):
                    // If the ACK timer has expired, send the ACK on its own.
                    send_frame(PFrame.ACK, 0, frame_expected, out_buf);
    				break;

    			default:
    				System.out.println("SWP: undefined event type = " + event.type);
    				System.out.flush();
    		}
    	}
    }

    /*
        Note: when start_timer() and stop_timer() are called,
        the "seq" parameter must be the sequence number, rather
        than the index of the timer array of the frame associated
        with this timer.
    */

	Timer frame_timer[] = new Timer[NR_BUFS];
	Timer ack_timer;

	public static int inc(int num) {
		num = ((num + 1) % (MAX_SEQ + 1));
	 	return num;
	}

	private void start_timer(int seq) {
		stop_timer(seq);
		frame_timer[seq % NR_BUFS] = new Timer();
		frame_timer[seq % NR_BUFS].schedule(new ReTask(swe, seq), 200);
	}

	private void stop_timer(int seq) {
		if (frame_timer[seq % NR_BUFS] != null) {
			frame_timer[seq % NR_BUFS].cancel();
		}
	}

	private void start_ack_timer( ) {
		stop_ack_timer();
		ack_timer = new Timer();
		ack_timer.schedule(new AckTask(swe), 100);
	}

	private void stop_ack_timer() {
		if (ack_timer != null) {
			ack_timer.cancel();
		}
	}

	// For Retransmission Timer:
	class ReTask extends TimerTask {
		private SWE swe = null;
		public int seqnr;

		public ReTask(SWE sw, int seq) {
			swe = sw;
			seqnr = seq;
		}

		public void run() {
			stop_timer(seqnr);
			swe.generate_timeout_event(seqnr);
		}
	}

	// For ACK Timer:
	class AckTask extends TimerTask {
		private SWE swe = null;

		public AckTask(SWE sw) {
			swe = sw;
		}

		public void run() {
			stop_ack_timer();
			swe.generate_acktimeout_event();
		}
	}
}


/*
    Note: In class SWE, the following two public methods are available:
    .generate_acktimeout_event() and
    .generate_timeout_event(seqnr).

    To call these two methods (for implementing timers),
    the "swe" object should be referred as follows:
    swe.generate_acktimeout_event(), or
    swe.generate_timeout_event(seqnr).
*/
