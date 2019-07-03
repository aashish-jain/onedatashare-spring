package org.onedatashare.server.service;

import org.apache.commons.codec.binary.Base64;
import org.codehaus.jackson.map.ObjectMapper;
import org.onedatashare.server.model.ticket.FreshdeskResponse;
import org.onedatashare.server.model.ticket.SupportTicketRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.mail.MessagingException;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * SupportTicketService is a service class that accepts the captured request from SupportTicketController
 * and connects to Freshdesk to create a ticket, returning the generated ticker number.
 *
 * Reference used for creating POST request in Java - https://www.mkyong.com/java/how-to-send-http-request-getpost-in-java/
 *
 * @version 1.0
 * @since 05-03-2019
 */
@Service
public class SupportTicketService {

    @Autowired
    EmailService emailService;

    @Autowired
    CaptchaService captchaService;

    @Value("${freshdesk.api.url}")
    private String FRESHDESK_API_URL;

    // Freshdesk account auth key through which tickets will be created
    private String FRESHDESK_API_KEY = System.getenv("FRESHDESK_API_KEY");

    private final String REQUEST_METHOD = "POST";
    private final String CONTENT_TYPE = "application/json";
    private final String CHARACTER_ENCODING = "utf-8";
    private final String ODS_TICKET_MAILBOX = "onedatasharetest@gmail.com";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // setting request authorization parameters. Using basic authentication
    // Format - Authorization : Basic <api_key>:<random_text_as_mock_password>
    private final String credential = (FRESHDESK_API_KEY + ":X");
    private final byte[] credentialEncBytes = Base64.encodeBase64(credential.getBytes()) ;
    private final String credentialB64Enc = new String(credentialEncBytes);

    /**
     * This method creates an http connection with Freshdesk, creates a ticket using the values passed
     * by the controller and returns the ticket number generated by Freshdesk.
     *
     * Note -
     * All the commented code contains logic for Google CAPTCHA validation for the support form.
     * When the CAPTHCHA validation needs to be turned on, uncomment all the commented code within this method.
     *
     * @param supportTicketRequest - Object containing request values
     * @return ticketNumber - An integer value returned by Freshdesk after generating the ticket
     */
    public Mono<Integer> createSupportTicket(SupportTicketRequest supportTicketRequest){

//        return captchaService.verifyValue(supportTicketRequest.getCaptchaVerificationValue())
//                .flatMap(captchaVerified-> {
//                    if (captchaVerified){
                        try {
                            URL urlObj = new URL(FRESHDESK_API_URL);
                            HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();

                            conn.setRequestMethod(REQUEST_METHOD);

                            conn.setRequestProperty("Authorization", "Basic " + credentialB64Enc);

                            conn.setRequestProperty("Content-Type", CONTENT_TYPE + "; " + CHARACTER_ENCODING);
                            conn.setRequestProperty("Accept", CONTENT_TYPE);
                            conn.setDoOutput(true);

                            String jsonBody =  supportTicketRequest.getRequestString().replace("\n","<br />");
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

                                FreshdeskResponse responseObj = objectMapper.readValue(response.toString(), FreshdeskResponse.class);

                                // Commenting out email trigger since Freshdesk would send notifications to ODS team
                                //sendEmail(responseObj);
                                return Mono.just(responseObj.getId());
                            }
                            else{
                                // Support ticket was not created by Freshdesk due to some error
                                ODSLoggerService.logError("An error occurred while trying to create a support ticket");
                                ODSLoggerService.logError("Response code : " + conn.getResponseMessage());
                            }
                        }
                        catch(MalformedURLException mue){
                            ODSLoggerService.logError("Exception occurred while creating URL object", mue);
                        }
                        catch(IOException ioe){
                            ODSLoggerService.logError("Exception occurred while opening or reading from a " +
                                                            "connection with " + FRESHDESK_API_URL, ioe);
                        }
                        catch (Exception ex){
                            ODSLoggerService.logError("General exception occurred while trying to create " +
                                                        "a support ticket", ex);
                        }

                        return Mono.error(new Exception("Error occurred while trying to create a support ticket"));
//                    }
//                    else
//                        return Mono.error(new Exception("Captcha verification failed"));
//                    }
//                );
    }    // createSupportTicket()

    /**
     * This method triggers an email on successful ticket creation.
     * The email is sent to ODS team members (common mailbox). Email contains details about the ticket.
     *
     * @param responseObj - Freshdesk server response on ticket creation
     */
    public void sendEmail(FreshdeskResponse responseObj){
        String subject = "Support ticket " + responseObj.getId() + " created";
        String emailText = "Ticket Details - \n" + responseObj.getDescription();

        try{
            emailService.sendEmail(ODS_TICKET_MAILBOX, subject, emailText);
        }
        catch (Exception ex){
            ODSLoggerService.logError("There was an error in sending ticket creation email", ex);
        }
    }

}    //class