package com.activegroup;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

public class App {
    public static void main(String[] args) throws Exception {
        // Create Jetty server on port 8080
        Server server = new Server(8080);

        // Set a simple request handler
        server.setHandler(new SimpleHandler());

        // Start the server
        server.start();
        server.join();
    }

    public static class SimpleHandler extends AbstractHandler {

        @Override
        public void handle(String target,
                           Request baseRequest,
                           HttpServletRequest servletRequest,
                           HttpServletResponse response)
                throws IOException {
            response.setContentType("application/ld+json; charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);

            // create an empty model
            Model model = ModelFactory.createDefaultModel();

            // create the resource
            Resource hirsch = model.createResource("https://hirsch-begegnungsstaette.de");
            hirsch.addProperty(SchemaDO.name, "Hirsch Begegnungsstätte für Ältere e.V.");

            // Create the address resource
            Resource address = model.createResource()
                .addProperty(SchemaDO.streetAddress, "Hirschgasse 9")
                .addProperty(SchemaDO.postalCode, "72072")
                .addProperty(SchemaDO.addressLocality, "Tübingen")
                .addProperty(SchemaDO.addressCountry, "DE");

            // add the property
            hirsch.addProperty(SchemaDO.address, address);

            // write to response
            model.write(response.getWriter(), "JSON-LD");

            // Mark request as handled
            baseRequest.setHandled(true);
        }
    }

}
