import java.io.BufferedInputStream;  
import java.io.BufferedOutputStream;  
import java.io.DataInputStream;  
import java.io.File;  
import java.io.FileInputStream;  
import java.io.IOException;  
import java.io.InputStreamReader;  
import java.io.OutputStream;  
import java.io.OutputStreamWriter;  
import java.io.Reader;  
import java.io.Writer;  
import java.net.Socket;  
import java.util.Date;  
import java.util.List;  
import java.util.LinkedList;  
import java.util.StringTokenizer;  
  
  
public class RequestProcessor implements Runnable {  
  
    private static List pool=new LinkedList();  
    private File documentRootDirectory;  
    private String indexFileName="index.html";  
      
    public RequestProcessor(File documentRootDirectory,String indexFileName) {  
        if (documentRootDirectory.isFile()) {  
            throw new IllegalArgumentException();  
        }  
        this.documentRootDirectory=documentRootDirectory;  
        try {  
            this.documentRootDirectory=documentRootDirectory.getCanonicalFile();  
        } catch (IOException e) {  
        }  
          
        if (indexFileName!=null) {  
            this.indexFileName=indexFileName;  
        }  
    }  
      
    public static void processRequest(Socket request) {  
        synchronized (pool) {  
            pool.add(pool.size(),request);  
            pool.notifyAll();  
        }  
    }  
      
    @Override  
    public void run() {  
        //安全性检测  
        String root=documentRootDirectory.getPath();  
          
        while (true) {  
            Socket connection;  
            synchronized (pool) {  
                while (pool.isEmpty()) {  
                    try {  
                        pool.wait();  
                    } catch (InterruptedException e) {  
                    }  
                      
                }  
                connection=(Socket)pool.remove(0);  
            }  
              
            try {  
                String fileName;  
                String contentType;  
                OutputStream raw=new BufferedOutputStream(connection.getOutputStream());  
                Writer out=new OutputStreamWriter(raw);  
                Reader in=new InputStreamReader(new BufferedInputStream(connection.getInputStream()), "ASCII");  
                  
                StringBuffer request=new StringBuffer(80);  
                while (true) {  
                    int c=in.read();  
                    if (c=='\t'||c=='\n'||c==-1) {  
                        break;  
                    }  
                    request.append((char)c);  
                }  
                  
                String get=request.toString();  
                //记录日志  
                System.out.println(get);  
                  
                StringTokenizer st=new StringTokenizer(get);  
                String method=st.nextToken();  
                String version="";  
                if ("GET".equals(method)) {  
                    fileName=st.nextToken();  
                    if (fileName.endsWith("/")) {  
                        fileName+=indexFileName;  
                    }  
                    contentType=guessContentTypeFromName(fileName);  
                    if (st.hasMoreTokens()) {  
                        version=st.nextToken();  
                    }  
                      
                    File theFile=new File(documentRootDirectory,fileName.substring(1,fileName.length()));  
                    if (theFile.canRead()&&theFile.getCanonicalPath().startsWith(root)) {  
                        DataInputStream fis=new DataInputStream(new BufferedInputStream(new FileInputStream(theFile)));  
                        byte[] theData=new byte[(int)theFile.length()];  
                        fis.readFully(theData);  
                        fis.close();  
                        if (version.startsWith("HTTP ")) {  
                            out.write("HTTP/1.0 200 OK\r\n");  
                            Date now=new Date();  
                            out.write("Date: "+now+"\r\n");  
                            out.write("Server: JHTTP 1.0\r\n");  
                            out.write("Content-length: "+theData.length+"\r\n");  
                            out.write("Content-Type: "+contentType+"\r\n\r\n");  
                            out.flush();  
                        }  
                        raw.write(theData);  
                        raw.flush();  
                    }else {  
                        if (version.startsWith("HTTP ")) {  
                            out.write("HTTP/1.0 404 File Not Found\r\n");  
                            Date now=new Date();  
                            out.write("Date: "+now+"\r\n");  
                            out.write("Server: JHTTP 1.0\r\n");  
                            out.write("Content-Type: text/html\r\n\r\n");  
                            out.flush();  
                        }  
                        out.write("<HTML>\r\n");  
                        out.write("<HEAD><TITLE>File Not Found</TITLE></HRAD>\r\n");  
                        out.write("<BODY>\r\n");  
                        out.write("<H1>HTTP Error 404: File Not Found</H1>");  
                        out.write("</BODY></HTML>\r\n");  
                    }  
                }else {//方法不等于"GET"  
                    if (version.startsWith("HTTP ")) {  
                        out.write("HTTP/1.0 501 Not Implemented\r\n");  
                        Date now=new Date();  
                        out.write("Date: "+now+"\r\n");  
                        out.write("Server: JHTTP 1.0\r\n");  
                        out.write("Content-Type: text/html\r\n\r\n");  
                        out.flush();  
                    }  
                    out.write("<HTML>\r\n");  
                    out.write("<HEAD><TITLE>Not Implemented</TITLE></HRAD>\r\n");  
                    out.write("<BODY>\r\n");  
                    out.write("<H1>HTTP Error 501: Not Implemented</H1>");  
                    out.write("</BODY></HTML>\r\n");  
                }  
                  
            } catch (IOException e) {  
            }finally{  
                try {  
                    connection.close();  
                } catch (IOException e2) {  
                }  
                  
            }  
        }  
    }  
      
    public static String guessContentTypeFromName(String name) {  
        if (name.endsWith(".html")||name.endsWith(".htm")) {  
            return "text/html";  
        }else if (name.endsWith(".txt")||name.endsWith(".java")) {  
            return "text/plain";  
        }else if (name.endsWith(".gif")) {  
            return "image/gif";  
        }else if (name.endsWith(".class")) {  
            return "application/octet-stream";  
        }else if (name.endsWith(".jpg")||name.endsWith(".jpeg")) {  
            return "image/jpeg";  
        }else {  
            return "text/plain";  
        }  
    }  
  
}  
