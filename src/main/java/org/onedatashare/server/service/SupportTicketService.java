package org.onedatashare.server.service;

import org.codehaus.jackson.map.ObjectMapper;
import org.onedatashare.server.model.core.SupportTicket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * SupportTicketService is a service class that accepts the captured request from SupportTicketController
 * and connects to the Redmine server to create a ticket, returning the generated ticker number.
 *
 * Reference used for creating POST request in Java - https://www.mkyong.com/java/how-to-send-http-request-getpost-in-java/
 *
 * @author Linus Castelino
 * @version 1.0
 * @since 05-03-2019
 */
@Service
public class SupportTicketService {

    @Value("${redmine.server.url}")
    private String REDMINE_SERVER_ISSUES_URL;

    // Redmine account auth key through which tickets will be created
    private String REDMINE_AUTH_KEY = System.getenv("REDMINE_AUTH_KEY");

    private final String REQUEST_METHOD = "POST";
    private final String CONTENT_TYPE = "application/json";
    private final String CHARACTER_ENCODING = "utf-8";

    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * This method creates an http connection with the Redmine server, creates a ticket using the values passed
     * by the controller and returns the ticket number generated by Redmine server.
     *
     * @param supportTicket - Object containing request values
     * @return ticketNumber - An integer value returned by Redmine server after generating the ticket
     */
    public Integer createSupportTicket(SupportTicket supportTicket){

        try {
            URL urlObj = new URL(REDMINE_SERVER_ISSUES_URL + "?key=" + REDMINE_AUTH_KEY);
            HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();

            conn.setRequestMethod(REQUEST_METHOD);
            conn.setRequestProperty("Content-Type", CONTENT_TYPE + "; " + CHARACTER_ENCODING);
            conn.setRequestProperty("Accept", CONTENT_TYPE);
            conn.setDoOutput(true);

            String jsonBody =  supportTicket.getRequestString();
            DataOutputStream outputStream = new DataOutputStream(conn.getOutputStream());
            outputStream.writeBytes(jsonBody);
            outputStream.flush();
            outputStream.close();

            int responseCode = conn.getResponseCode();
            if(responseCode == HttpURLConnection.HTTP_CREATED){
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                String input = null;
                StringBuffer response = new StringBuffer();
                while((input = br.readLine()) != null)
                    response.append(input);

                br.close();

                System.out.println(response);
            }


        }
        catch(MalformedURLException mue){
            System.out.println("Exception occurred while creating URL object");
            mue.printStackTrace();
        }
        catch(IOException ioe){
            System.out.println("Exception occurred while opening or reading from a connection with " + REDMINE_SERVER_ISSUES_URL);
            ioe.printStackTrace();
        }

        return null;
    }
}



/*
Sample JSON response from Redmine for issue creation :
{
    "issue": {
        "id": 6,
        "project": {
            "id": 2,
            "name": "test"
        },
        "tracker": {
            "id": 1,
            "name": "Bug"
        },
        "status": {
            "id": 1,
            "name": "New"
        },
        "priority": {
            "id": 2,
            "name": "Normal"
        },
        "author": {
            "id": 1,
            "name": "Linus Castelino Admin"
        },
        "subject": "test issue",
        "description": "TEst description",
        "start_date": "2019-05-04",
        "due_date": null,
        "done_ratio": 0,
        "is_private": false,
        "estimated_hours": null,
        "total_estimated_hours": null,
        "created_on": "2019-05-04T22:08:07Z",
        "updated_on": "2019-05-04T22:08:07Z",
        "closed_on": null
    }
}
 */