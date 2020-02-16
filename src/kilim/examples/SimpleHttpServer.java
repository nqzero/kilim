/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim.examples;

import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import kilim.ForkJoinScheduler;

import kilim.Pausable;
import kilim.Scheduler;
import kilim.http.HttpRequest;
import kilim.http.HttpResponse;
import kilim.http.HttpServer;
import kilim.http.HttpSession;
import kilim.http.KeyValues;

/**
 * A basic HTTP server that merely echoes the path and the query string supplied to it in a GET request
 * 
 * Usage: Run java kilim.examples.HttpFileServer [port] [fjpNumThreads]
 *   default port: 7262
 *   if fjpNumThreads isn't specified, the default scheduler is used
 * 
 * From a browser, try "http://localhost:7262/hello", "http://localhost:7262/buy?code=200&desc=Rolls%20Royce">"
 * 
 * SimpleHttpSession is an HTTPSession task, itself a thin wrapper over the socket connection. An instance of this
 * task is launched for each new connection, and its execute method is called when the task is scheduled. 
 *
 * <p>
 * The HttpRequest and HttpResponse objects are wrappers over a bytebuffer,
 * and unrelated to the socket. The request object is "filled in" by HttpSession.readRequest() and the response object
 * is sent by HttpSession.sendResponse(). 
 */

public class SimpleHttpServer {

    public static void main(String[] args) throws IOException {
        int port = 7262;
        try { port = Integer.parseInt(args[0]); } catch (Throwable ex) {}
        try {
            int num = Integer.parseInt(args[1]);
            Scheduler.setDefaultScheduler(new ForkJoinScheduler(num));
        }
        catch (Throwable ex) {}
        System.out.println("Default Scheduler: " + Scheduler.getDefaultScheduler());
        new HttpServer(port, SimpleHttpSession.class);
        System.out.println("SimpleHttpServer listening on http://localhost:" + port);
        System.out.format("From a browser, "
                + "try http://localhost:%d/hello\n "
                + "or http://localhost:%d/buy?code=200&desc=Rolls%%20Royce\n", port, port);
    }
    
    public static class SimpleHttpSession extends HttpSession {

        @Override
        public void execute() throws Pausable, Exception {
            try {
                // We will reuse the req and resp objects
                HttpRequest req = new HttpRequest();
                HttpResponse resp = new HttpResponse();
                while (true) {
                    super.readRequest(req);
                    if (req.keepAlive())
                        resp.addField("Connection", "Keep-Alive");
                    if (req.method.equals("GET") || req.method.equals("HEAD")) {
                        resp.setContentType("text/html");
                        PrintWriter pw = new PrintWriter(resp.getOutputStream());
                        // Note that resp.getOutputStream() does not write to the socket; it merely buffers the entire
                        // output. 
                        pw.append("<html><body>path = ");
                        pw.append(req.uriPath).append("<p>");
                        KeyValues kvs = req.getQueryComponents();
                        for (int i = 0; i < kvs.count; i++) {
                            pw.append(kvs.keys[i]).append(" = ").append(kvs.values[i]).append("<br>");
                        }
                        pw.append("</body></html>");
                        pw.flush();
                        sendResponse(resp);
                    } else {
                        super.problem(resp, HttpResponse.ST_FORBIDDEN, "Only GET and HEAD accepted");
                    }
                    
                    if (!req.keepAlive()) 
                        break;
                }
            } catch (EOFException e) {
                System.out.println("[" + this.id + "] Connection Terminated");
            } catch (IOException ioe) {
                System.out.println("[" + this.id + "] IO Exception:" + ioe.getMessage());
            }
            super.close();
        }
    }
}