//steruje dyksami, cała synchronizacja

import java.io.*;
import java.nio.file.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class Multi_Client extends Thread {
	
	Socket socket;
	ServerSocket server;
	PrintWriter pw;
	Scanner scan;
	String msg;
	/**Multi_Client constructor that takes ServerSocket and Socket as arguments.
	 * @param server ServerSocket took form Server class
	 * @param socket Socket took form Server class*/
	public Multi_Client(ServerSocket server, Socket socket) throws IOException{
		this.server=server;
		this.socket=socket;
		init();
	}
	/**Initializes PrintWriter, Scanner and String*/
	public void init() throws IOException {
		this.pw = new PrintWriter(socket.getOutputStream(), true);
		this.scan = new Scanner(socket.getInputStream());
		this.msg = new String();			
	}
	/**Chooses disc with the least amount of space taken*/
	public String choose_disc() throws IOException{
		int index=1;
		long min = 0;
		for(int i=0; i<5; i++) {
			String server_path = "Server\\d" + (i+1) + "\\";
			long size = Files.walk(Paths.get(server_path)).mapToLong( p -> p.toFile().length()).sum();//exception
			if(i == 0) min = size;
			if(min >= size) {
				min = size;
				index = i+1;
			}
		}
		return "d"+index;
	}
	/**Gets MyMap from client with his files list.*/
	public MyMap get_client_files_map () throws Exception{
		ObjectInputStream mapInStream = new ObjectInputStream(socket.getInputStream());//exception
		MyMap map = (MyMap) mapInStream.readObject();//ioexception, classnotfoundexception
		//mapInStream.close();//exception
		return map;
	}
	/**Returns a list of uncommon files between two lists.
	 * @param want list from which you delets files to leave files hat you want
	 * @param dont list os files that you dont want*/
	public List<String> uncommon_files(List<String> want, List<String> dont){
		List<String> notPresent = new ArrayList<String>(want);
		notPresent.removeAll(dont);

		//System.out.println("List of not existing files:"); //uruchamiane na serwerze daje pliki dla cienta, i odwrotnie
		//System.out.println(notPresent);
		return notPresent;
	}
	/**Sends list of files that client dont have to him
	 * @param list list that will be send to client
	 * @param stream stream used to send list*/
	static public void send_files_list (List<String> list, OutputStream stream) throws Exception {
        TimeUnit.SECONDS.sleep(5);
		ObjectOutputStream listOutStream = new ObjectOutputStream(stream);
		listOutStream.writeObject(list);
    }
	/**Gets lists of clients from all discs
	 * @param client client that will be removed from list*/
    public static List<String> get_clients_list(String client) throws Exception{
        Discs disc = new Discs();
        List<String> clients = new ArrayList<String>();
        MyMap[] filelist = new MyMap[5];

        for(int i=0; i<5; i++){
			filelist[i] = disc.get_csv("d" + (i+1));
			for(String key : filelist[i].keySet()){
				if(!clients.contains(key))
					clients.add(key);
			}	
		}
		clients.remove(client);
		return clients;
    }
	/**Synchronizes saving files on client side.
	 * @param filelist list of files from all discs
	 * @param client_files list of client files*/
	public void synchro_client(MyMap[] filelist, MyMap client_files) throws Exception{//from client to server
		Discs disc = new Discs();
		List<String>[] withDisc = new ArrayList[5];
		List<String> dontHave = new ArrayList<String>();
		List<String> files = new ArrayList<String>();

		Server.status="Sendig files to client...";

		Set<String> client = client_files.keySet();

		for(int i=0; i<5; i++){//zmienić na 5
			//System.out.println((i+1)+":");
			withDisc[i] = new ArrayList<String>();
			filelist[i] = disc.get_csv("d" + (i+1));//ioexception
			for(String key: client){
				if(filelist[i].containsKey(key)){
					if(files.isEmpty()) files.addAll(client_files.get(key));
					withDisc[i].addAll(filelist[i].get(key));
				}
			}
			withDisc[i] = uncommon_files(withDisc[i], files);
			for(ListIterator<String> u = withDisc[i].listIterator(); u.hasNext(); ){
				dontHave.add("Server\\d" + (i+1) + "\\" + u.next());
			}
			//System.out.println(i + ":" + withDisc[i]);
		}
		
		// System.out.println("Te ktorych klient nie ma: ");
		// System.out.println(dontHave);
		send_files_list(dontHave, socket.getOutputStream());

	}
	/**Synchronizes saving files on server side.
	 * @param filelist list of files from all discs
	 * @param client_files list of client files
	 * @param client_path path to client folder*/
	public void synchro_server(MyMap[] filelist, MyMap client_files, String client_path) throws Exception{//from server to client
		Discs disc = new Discs();

		List<String> dontHave = new ArrayList<String>();
		List<String> files = new ArrayList<String>();

		Set<String> client = client_files.keySet();
		String client_name = new String();

		for(int i=0; i<5; i++){//zmienić na 5
			//System.out.println((i+1)+":");
			filelist[i] = disc.get_csv("d" + (i+1));//ioexception
			for(String key: client){
				if(client_name.isEmpty()) client_name = key;
				if(files.isEmpty()) files.addAll(client_files.get(key));
				if(filelist[i].containsKey(key))
					//dontHave = uncommon_files(filelist[i].get(key),client_files.get(key));
					dontHave.addAll(filelist[i].get(key));
			}
		}

		dontHave = uncommon_files(files, dontHave);
		
		if(dontHave.isEmpty()) {
			Server.status="Nothing to be saved on server";
			this.pw.println("0");
		}
		else{
			Server.status="Saving client files...";
			for(ListIterator<String> u = dontHave.listIterator(); u.hasNext(); ){
				u.set(client_path + "\\" + u.next());
			}

			// System.out.println("Te ktorych server nie ma: ");
			// System.out.println(dontHave);

			//how many files will be saved on server
			this.pw.println(dontHave.size());

			System.out.println("Saving files on server...");
			for(ListIterator<String> u = dontHave.listIterator(); u.hasNext(); ){
				new Discs(choose_disc(), client_name, u.next(), this.pw).start();
				//disc.copy_file("Server\\" + choose_disc() + "\\", u.next());
			}
			//System.out.println("Files saved.");
			//this.pw.println("Saved");
			}
	}
	/**Synchronizes all sending and receiving files between client and server
	 * @param stream stream from which client files will be taken
	 * @param client_path path to client folder*/
	public void synchro(InputStream stream, String client_path) throws Exception{
		Discs disc = new Discs();

		Server.status="Synchronizing...";
		System.out.println("Synchronizing...");
		TimeUnit.SECONDS.sleep(5);

		System.out.println("Reading .csv");
		//List<String> dontHave = new ArrayList<String>();

		MyMap client_files = get_client_files_map();

		//got files map
		this.pw.println("Got");
		
		MyMap[] filelist = new MyMap[5];
		
		//System.out.println("SERVER");
		synchro_server(filelist, client_files, client_path);

        //Client knows that all files has been saved
		this.msg = scan.nextLine();
        if(!this.msg.equals("Have")){
            System.out.println("Error while synchronizing");
        }

		//System.out.println("CLIENT");
		synchro_client(filelist, client_files);
		//Client got files list
		this.msg = scan.nextLine();
        if(!this.msg.equals("Got")){
            System.out.println("Error while synchronizing");
        }
        //how many files will be saved
        msg = scan.nextLine();
        try{
            Integer.valueOf(msg);
        }catch(NumberFormatException  e){
            System.out.println("Error while saving files");
        }

        int how_many = Integer.valueOf(msg);
        if(how_many == 0) System.out.println("Nothing to be saved");
        else {
        	for(int i=0; i<how_many; i++){
	            msg = scan.nextLine();
	            //one file has been saved
	            if(!msg.equals("Saved")){
	                System.out.println("Error while copying file");
	            }
	        }
        }
        //Server knows that all files has been saved
        pw.println("OK");

        //EVERYTHING IS DONE
        Server.status="Done";
        this.msg = scan.nextLine();
        if(!this.msg.equals("Done")){
            System.out.println("Error while copying files");
        }
	}
	/**If client and server are ready it starts synchronization, and control status of it.*/
	public void run(){ 
		try {
			try {
				String path = new String();
				
				String client = this.scan.nextLine();
				String client_path = this.scan.nextLine();
				System.out.println("Connected to " + client +".");

				while(true){
					//Server is ready
					this.pw.println("Ready");
					//Client is ready
	                this.msg = this.scan.nextLine();
	                if(!this.msg.equals("Ready")){
	                    System.out.println("Error while connecting to client " + client);
	                }
					
					synchro(socket.getInputStream(), client_path);

					System.out.println("Synchronized");
					TimeUnit.SECONDS.sleep(10);

				}
			} catch (Exception e) {
				//e.printStackTrace();
				System.out.println("Connection error");
			}
        } catch (Exception e) {
            //e.printStackTrace();
            System.out.println("Server error");
        } 
	}  
}