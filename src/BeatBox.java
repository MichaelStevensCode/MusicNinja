import java.awt.*;
import javax.swing.*;
import java.io.*;

import javax.sound.midi.*;
import java.util.*;
import java.awt.event.*;
import java.net.*;
import javax.swing.event.*;


public class BeatBox{
    
    JFrame theFrame;
    JPanel mainPanel;
    JList incomingList;
    JTextField userMessage;
    ArrayList<JCheckBox> checkBoxList;
    int nextNum;
    Vector<String> listVector = new Vector<String>();
    String userName;
    ObjectOutputStream out;
    ObjectInputStream in;
    HashMap<String, boolean[]> otherSeqMap = new HashMap<String, boolean[]>();

    Sequencer sequencer;
    Sequence sequence;
    Sequence mysequence = null;
    Track track;
    
    String[] instrumentNames = {
        "Bass Drum","Closed Hi-Hat","Open Hi-Hat","Acoustic Snare",
        "Crash Cymbal","Hand Clap","High Tom","Hi Bongo","Maracas","Whistle",
        "Low Conga","Cowbell","Vibraslap","Low-mid Tom","High Agogo","Open Hi Conga"
    };
    int[] instruments ={35,42,46,38,49,39,50,60,70,72,64,56,58,47,67,63};

    public static void main(String[] args){
        // args[0] is the name of the user
        new BeatBox().startUp(args[0]);
    }
    public void startUp(String name){
        userName=name;
        // opening connection to the server
        try{
            Socket sock = new Socket("127.0.0.1",4242);
            out = new ObjectOutputStream(sock.getOutputStream());
            in = new ObjectInputStream(sock.getInputStream());
            Thread remote = new Thread(new RemoteReader());
            remote.start();
        }catch(Exception ex){
            System.out.println("Couldn't Connect - You will have to play alone.");
        }
        setupMIDI();
        buildGUI();
    }
    public void buildGUI(){
        theFrame = new JFrame("Music Ninja");
        BorderLayout layout=new BorderLayout();
        JPanel background = new JPanel(layout);
        background.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        checkBoxList = new ArrayList<JCheckBox>();
        Box buttonBox = new Box(BoxLayout.Y_AXIS);
        JButton start = new JButton("Start");
        start.addActionListener(new startListener());
        buttonBox.add(start);

        JButton stop = new JButton("Stop");
        stop.addActionListener(new stopActionListner());
        buttonBox.add(stop);

        JButton uptempo = new JButton("Tempo up");
        uptempo.addActionListener(new upTempoListener());
        buttonBox.add(uptempo);

        JButton downTempo = new JButton("Tempo down");
        downTempo.addActionListener(new downTempoListener());
        buttonBox.add(downTempo);
        
        JButton sendIt = new JButton("Send it");
        sendIt.addActionListener(new sendActionListener());
        buttonBox.add(sendIt);

        userMessage = new JTextField();
        buttonBox.add(userMessage);

        incomingList = new JList();
        incomingList.addListSelectionListener(new MylistSelectionListener());
        incomingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane theList = new JScrollPane(incomingList);
        buttonBox.add(theList);
        incomingList.setListData(listVector);

        Box nameBox = new Box(BoxLayout.Y_AXIS);
        for(int i=0;i<16;i++){
            nameBox.add(new Label(instrumentNames[i]));
        }

        background.add(BorderLayout.EAST,buttonBox);
        background.add(BorderLayout.WEST,nameBox);

        theFrame.getContentPane().add(background);
        GridLayout grid = new GridLayout(16,16);
        grid.setVgap(1);
        grid.setHgap(2);
        mainPanel = new JPanel(grid);
        background.add(BorderLayout.CENTER,mainPanel);

        for(int i=0;i<256;i++){
            JCheckBox c = new JCheckBox();
            c.setSelected(false);
            checkBoxList.add(c);
            mainPanel.add(c);
        }

        theFrame.setBounds(50, 50, 300, 300);
        theFrame.pack();
        theFrame.setVisible(true);
    }

    public void setupMIDI(){
        try{
            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            sequence = new Sequence(Sequence.PPQ, 4);
            track = sequence.createTrack();
            sequencer.setTempoInBPM(120);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void buildTrackandStart(){
        ArrayList<Integer> trackList = null;
        sequence.deleteTrack(track);
        track = sequence.createTrack();

        for(int i=0;i<16;i++){
            trackList = new ArrayList<Integer>();
            for(int j=0;j<16;j++){
                JCheckBox jc = (JCheckBox) checkBoxList.get(j+16*i);
                if(jc.isSelected()){
                    int key=instruments[i];
                    trackList.add(Integer.valueOf(key));
                }
                else{
                    trackList.add(null);
                }
            }
            makeTracks(trackList);
        }
        track.add(makeEvent(192,9,1,0,15));
        try{
            sequencer.setSequence(sequence);
            sequencer.setLoopCount(sequencer.LOOP_CONTINUOUSLY);
            sequencer.start();
            sequencer.setTempoInBPM(120);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public class startListener implements ActionListener{
        public void actionPerformed(ActionEvent a){
            buildTrackandStart();
        }
    }

    public class stopActionListner implements ActionListener{
        public void actionPerformed(ActionEvent a){
            sequencer.stop();
        }
    }

    public class upTempoListener implements ActionListener{
        public void actionPerformed(ActionEvent a){
            float tempoFactor =sequencer.getTempoFactor();
            sequencer.setTempoFactor((float)(tempoFactor*1.03));
        }
    }

    public class downTempoListener implements ActionListener{
        public void actionPerformed(ActionEvent a){
            float tempoFactor = sequencer.getTempoFactor();
            sequencer.setTempoFactor((float)(tempoFactor*0.97));
        }
    }

    public class sendActionListener implements ActionListener{
        public void actionPerformed(ActionEvent a){
            boolean[] checkBoxState = new boolean[256];
            for(int i=0;i<256;i++){
                JCheckBox check = (JCheckBox) checkBoxList.get(i);
                if(check.isSelected()){
                    checkBoxState[i]=true;
                }
            }
            try{
                out.writeObject(userName+nextNum++ +": "+userMessage.getText());
                out.writeObject(checkBoxState);
            }catch(Exception ex){
                System.out.println("Sorry your message could not be sent! try again later");
            }
            userMessage.setText("");
        }
    }

    public class MylistSelectionListener implements ListSelectionListener{
        public void valueChanged(ListSelectionEvent le){
            if(!le.getValueIsAdjusting()){
                String selected = (String) incomingList.getSelectedValue();
                if(selected!=null){
                    boolean[] selectedState = (boolean[]) otherSeqMap.get(selected);
                    changeSequence(selectedState);
                    sequencer.stop();
                    buildTrackandStart();
                }
            }
        }
    }
    public class RemoteReader implements Runnable{
        boolean[] checkBoxState = null;
        String nameToshow=null;
        Object obj = null;
        public void run(){
            try{
                while((obj=in.readObject())!=null){
                    System.out.println("Got an object from the server");
                    System.out.println(obj.getClass());
                    nameToshow=(String)obj;
                    checkBoxState = (boolean[]) in.readObject();
                    otherSeqMap.put(nameToshow, checkBoxState);
                    listVector.add(nameToshow);
                    incomingList.setListData(listVector);
                }
            }catch(Exception ex){
                ex.printStackTrace();
            }

        }
    }
    public class PlayMineListener implements ActionListener{
        public void actionPerformed(ActionEvent a){
            if(mysequence != null){
                sequence = mysequence;
            }
        }
    }
    public void changeSequence(boolean[] checkboxState){
        for(int i=0;i<256;i++){
            JCheckBox check = (JCheckBox) checkBoxList.get(i);
            if(checkboxState[i]){
                check.setSelected(true);
            }
            else{
                check.setSelected(false);
            }
        }
    }
    public void makeTracks(ArrayList list){
        Iterator it = list.iterator();
        for(int i=0;i<16;i++){
            Integer num=(Integer) it.next();
            if(num!=null){
                int numKey = num.intValue();
                track.add(makeEvent(144,9,numKey,100,i));
                track.add(makeEvent(128,9,numKey,100,i+1));
            }
        }
    }
    public MidiEvent makeEvent(int comd,int chan,int one,int two,int tick){
        MidiEvent event = null;
        try{
            ShortMessage a= new ShortMessage();
            a.setMessage(comd, chan, one, two);
            event = new MidiEvent(a, tick);
        }
        catch(Exception ex){}
        return event;
    }
}