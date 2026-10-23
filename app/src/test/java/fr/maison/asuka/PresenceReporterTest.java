package fr.maison.asuka;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class PresenceReporterTest {
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); int c;
        while((c=in.read())!=-1 && c!='\n') if(c!='\r') out.write(c);
        return out.toString("UTF-8");
    }
    @Test public void postsOnlyPresenceAndSurvives404WithoutFollowingRedirects() throws Exception {
        try(ServerSocket server=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(8000);
            FutureTask<List<String[]>> received=new FutureTask<>(() -> {
                List<String[]> requests=new ArrayList<>();
                for(int i=0;i<2;i++) try(Socket socket=server.accept()) {
                    socket.setSoTimeout(8000); InputStream in=socket.getInputStream();
                    assertEquals("POST /api/presence HTTP/1.1",line(in));
                    String header,token=""; int length=0;
                    while(!(header=line(in)).isEmpty()) {
                        int split=header.indexOf(':'); if(split<0) continue;
                        String key=header.substring(0,split), value=header.substring(split+1).trim();
                        if(key.equalsIgnoreCase("Content-Length")) length=Integer.parseInt(value);
                        if(key.equalsIgnoreCase("Authorization")) token=value;
                    }
                    byte[] content=new byte[length]; int offset=0,n;
                    while(offset<length && (n=in.read(content,offset,length-offset))>0) offset+=n;
                    assertEquals(length,offset); requests.add(new String[]{new String(content,StandardCharsets.UTF_8),token});
                    String response="HTTP/1.1 "+(i==0?"404 Not Found":"302 Found")+"\r\nLocation: http://127.0.0.1:1/never\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
                }
                return requests;
            });
            Thread thread=new Thread(received,"TestHouse"); thread.setDaemon(true); thread.start();
            Settings settings=new Settings(RuntimeEnvironment.getApplication());
            settings.p.edit().clear().putString("server","http://127.0.0.1:"+server.getLocalPort()+"/").putString("room","salle de bain").commit();
            PresenceReporter reporter=new PresenceReporter(settings,() -> "test-only-token");
            try {
                reporter.changed("teddy",true); reporter.changed("teddy",true);
                reporter.changed("standby",false);
                List<String[]> requests=received.get(10,TimeUnit.SECONDS);
                assertEquals(2,requests.size()); assertEquals("Bearer test-only-token",requests.get(0)[1]);
                JSONObject first=new JSONObject(requests.get(0)[0]);
                assertEquals("salle de bain",first.getString("piece")); assertTrue(first.getBoolean("teddy"));
                assertEquals(5,first.length()); assertTrue(first.getString("appareil").startsWith("asuka-"));
                assertNotNull(Instant.parse(first.getString("heure")));
                assertFalse(new JSONObject(requests.get(1)[0]).getBoolean("teddy"));
            } finally { reporter.close(); }
        }
    }
}
