package service;

import java.io.Writer;
import java.util.HashMap;

import IBMToneAnalyzerConnector.ToneAnalyzerConnector;

import daoCloudant.CloudantUsuarioDAO;

import dominio.Usuario;

import javassist.NotFoundException;

import kafka.KafkaMessagesConsumer;
import kafka.KafkaMessagesProducer;

public class UsuarioConnection implements MessageHandler{

		  //Un único productor puede enviar a múltiples chats, no es necesario más de uno
		  //Un cliente escucha a todos los posibles chats, el administrador de ws decide qué enviar y si enviar.

		  private Usuario usuario=null;
		  private HashMap <String, KafkaMessagesConsumer> receptores;
		  private HashMap <KafkaMessagesConsumer, Thread> matchingThread;
		  private KafkaMessagesProducer productor;
		  private String chat; //El chat al que el usuario esta escuchando
		  private Writer wsWriter; //Writer hacia el Websocket si es que este esta conectado

		  

		  public UsuarioConnection(String idUsuario) throws NotFoundException{

					 //Obtenemos el usuario
					 usuario=(new CloudantUsuarioDAO()).get(idUsuario);
					 if(usuario==null)
								throw new NotFoundException("Usuario No encontrado");

					 //Creamos el productor
					 productor=new KafkaMessagesProducer(usuario.getChats().iterator().next());

					 //Creamos los receptores
					 receptores=new HashMap<String, KafkaMessagesConsumer>();
					 matchingThread=new HashMap<KafkaMessagesConsumer, Thread>();
					 for(String chat: usuario.getChats()){
								KafkaMessagesConsumer consumer= new KafkaMessagesConsumer(chat,this);
								Thread consumerThread = new Thread(consumer, "consumer Thread");
								consumerThread.start();
								receptores.put(chat, consumer);
								matchingThread.put(consumer, consumerThread);
					 }

		  }

		  public UsuarioConnection(String idUsuario, String chat) throws NotFoundException{
					 this(idUsuario);
					 this.chat = chat;
		  }

		  public UsuarioConnection(String idUsuario, String chat, Writer wsWriter) throws NotFoundException{
					 this(idUsuario);
					 this.chat = chat;
					 this.wsWriter = wsWriter;
		  }

		  public Usuario getUsuario() {
					 return usuario;
		  }



		  public void setUsuario(Usuario usuario) {
					 this.usuario = usuario;
		  }

		  //TODO: cambiar el chat para mejor soporte gson en tono y usuario
		  public void enviarMensaje(String mensaje){
					 String sentimiento=ToneAnalyzerConnector.getToneAnalyzer().analyzeText(mensaje);
					 mensaje=sentimiento+"@"+mensaje; //TODO: añadir al objeto mensaje

					 productor.send(this.chat, mensaje); 
		  }

		  public void salirChat(String chat){

					 KafkaMessagesConsumer consumer = receptores.get(chat);
					 consumer.shutdown(); //Cerrar conexión
					 //Esperar a que se cierre la conexión
					 try {
								Thread.sleep(4000);
					 } catch (InterruptedException e) {
								e.printStackTrace();
					 }

					 matchingThread.get(consumer).interrupt(); //Cerrar Thread

					 receptores.remove(chat); //Eliminar de la lista de receptores
					 matchingThread.remove(consumer); //Eliminar de la lista de matching;

					 usuario.removeChat(chat); //Eliminar del usuario
					 (new CloudantUsuarioDAO()).persist(usuario);

		  }

		  public void entrarChat(String chat){

					 KafkaMessagesConsumer consumer= new KafkaMessagesConsumer(chat, this);
					 Thread consumerThread = new Thread(consumer, "consumer Thread");
					 consumerThread.start();
					 receptores.put(chat, consumer);
					 matchingThread.put(consumer, consumerThread);


					 usuario.addChat(chat);
					 (new CloudantUsuarioDAO()).persist(usuario);
		  }

		  public void eliminateChat(){ //Dejar de escuchar al chat
					 this.chat = null;
		  }



		  public void close(){

					 productor.shutdown();

					 for(KafkaMessagesConsumer kf: receptores.values()){
								kf.shutdown();
					 }

					 //Necesario esperare a que se cierren todos los consumidores
					 try {
								Thread.sleep(4000);
					 } catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
					 }

					 for(Thread kf: matchingThread.values()){
								kf.interrupt();
					 }

		  }

		  @Override
		  public void onMessage( String msg, String room ){
					 //Gestionar mensajes kafka
					 try{
								if( room == this.chat )
										  this.wsWriter.write( msg );
					 }
					 catch(Exception e){
								e.printStackTrace();
					 }
		  }
}
