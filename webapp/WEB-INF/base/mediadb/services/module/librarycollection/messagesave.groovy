import org.entermediadb.asset.MediaArchive
import org.entermediadb.websocket.chat.ChatManager
import org.entermediadb.websocket.chat.ChatServer
import org.openedit.Data
import org.openedit.WebPageRequest


public void init(){
	WebPageRequest req = context;
	MediaArchive archive = req.getPageValue("mediaarchive");
	Data data = req.getPageValue("data");
	
	data.setValue("user",req.getUserName());
	data.setValue("date",new Date());
	archive.saveData("chatterbox", data);
	
	ChatServer server  = (ChatServer) archive.getModuleManager().getBean("system", "chatServer");
	
	server.broadcastMessage(archive.getCatalogId(),data);

	//Google notify
	archive.fireDataEvent(req.getUser(), "chatterbox", "messagereceived", data);
	
	
}

init();