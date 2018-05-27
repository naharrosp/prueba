/*El chat envía y recibe toda la información a través de este punto.
  Los mensajes recibidos tendrán la siguiente estructura:
  -Userid: int (Identificador de usuario)
  -RoomId: int (Identificador de chat, posiblemente una string sea válida)
  -Msg: string (contenido del mensaje)

  Los mensajes enviados tendrán la siguiente estructura:

*/

package servlet;

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.google.gson.Gson;

import dominio.LoginMessage;

import service.RoomService;
import service.UsuarioConnection;


@ServerEndpoint("/wsEndpoint")
public class chatWebsocket {

		  private static Map<String,UsuarioConnection> wsUser; //websocketid -usuario

		  @OnOpen
		  public void open(Session session) {
					 // Identificar al usuario del websocket
					 // Comunicar con kafka el color del usuario
					 if( wsUser == null )
								wsUser = new HashMap<String,UsuarioConnection>();
		  }

		  @OnClose
		  public void close(Session session) {
					 String id = session.getId();
					 wsUser.get(id).close();
					 wsUser.remove(id);
		  }

		  @OnError
		  public void onError(Session session, Throwable error) {
					 String id = session.getId();
					 wsUser.get(id).close();
					 wsUser.remove(id);
		  }

		  @OnMessage //Una vez el usuario esté registrado dar el handler al RoomUser
		  public void handleMessage(String message, Session session) {
					 // No es necesario identificar al usuario, solamente hace falta enviar mensaje a kafka
					 //if( !RoomService.isClientReg( id ) ){
					 System.out.print("Recibido mensaje de Websocket" + message);
					 String sessionId = session.getId();
					 Writer wsWriter = null;
					 if(wsUser.containsKey(sessionId)){
								try{
										  Gson gson = new Gson();
										  LoginMessage msg = gson.fromJson( message, LoginMessage.class );
										  String userId = msg.getId();
										  String room = msg.getRoom();
										  if(!RoomService.loginWebSocket(userId, room))
													 throw new Exception("El usuario no esta en la sala de espera");
										  //user = RoomUser.userFactory( msg.getId(), msg.getRoom());
										  wsWriter = session.getBasicRemote().getSendWriter();
										  UsuarioConnection uc = new UsuarioConnection(userId, room, wsWriter);
										  wsUser.put(sessionId, uc);
										  //TODO: Añadir soporte gson
										  wsWriter.write("{status: connected}");
								}
								catch(Exception ex){
										  String json = (new Gson()).toJson(ex); //Se envía el error a través de json
										  try{
													 if(wsWriter != null)
																wsWriter.write(json);
													 else
																ex.printStackTrace();
										  }
										  catch(Exception e){
													 e.printStackTrace();
										  }
								}
					 }else{ // Ya existe el usuario
								//Utilizar GSON
								wsUser.get(sessionId).enviarMensaje(message);

					 }
		  }


}    

