package org.onedatashare.server.module.googledrive;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.onedatashare.server.model.core.Credential;
import org.onedatashare.server.model.core.Session;
import org.onedatashare.server.model.credential.OAuthCredential;
import org.onedatashare.server.model.error.AuthenticationRequired;
import org.onedatashare.server.model.useraction.IdMap;
import reactor.core.publisher.Mono;
import org.onedatashare.server.service.oauth.GoogleDriveOauthService;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class GoogleDriveSession  extends Session<GoogleDriveSession, GoogleDriveResource> {
    private static GoogleClientSecrets clientSecrets;
    static transient Drive service;
    private transient HashMap<String, String> pathToParentIdMap = new HashMap<>();
    protected ArrayList<IdMap> idMap = null;
    private static GoogleAuthorizationCodeFlow flow;

    private static String APPLICATION_NAME = "OneDataShare";
    private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"), ".credentials/ods");
    private static FileDataStoreFactory DATA_STORE_FACTORY;
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static HttpTransport HTTP_TRANSPORT;
    private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE_READONLY);

    public GoogleDriveSession(URI uri, Credential credential) {
        super(uri, credential);
    }

    @Override
    public Mono<GoogleDriveResource> select(String path) {
        return Mono.just(new GoogleDriveResource(this, path));
    }
    @Override
    public Mono<GoogleDriveResource> select(String path, String id, ArrayList<IdMap> idMap) {
        this.idMap = idMap;
        if(idMap !=null && idMap.size()>0)
            for (IdMap idPath: idMap) {
                pathToParentIdMap.put(idPath.getPath(),idPath.getId());
            }
        return Mono.just(new GoogleDriveResource(this, path,id));
    }

    @Override
    public Mono<GoogleDriveSession> initialize() {
        return Mono.create(s -> {
            if(credential instanceof OAuthCredential){
                try{
                    service = getDriveService(((OAuthCredential) credential).token);
                    System.out.println("Service: "+service);
                }catch(Throwable t) {
                    throw new RuntimeException(t);
                }
                if(service !=null)
                    s.success(this);
                else {
                        OAuthCredential c = updateToken();
                        s.error(new AuthenticationRequired(401,c, "Bad Request"));
                }
            }
            else s.error(new AuthenticationRequired("oauth"));
        });
    }


    public static void initGoogle() {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        GoogleDriveOauthService.GoogleDriveConfig c = new GoogleDriveOauthService.GoogleDriveConfig();
        List<String> redirect_uris;

        if (c != null && c.client_id != null && c.client_secret != null && c.redirect_uris != null) {
            redirect_uris = Arrays.asList(c.redirect_uris/*.replaceAll("\\[|\\]|\"|\n","")*/
                    .trim()
                    .split(","));
            String finishURI = redirect_uris.get(0);

            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();

            details.setAuthUri(c.auth_uri).setClientId(c.client_id)
                    .setClientSecret(c.client_secret).setRedirectUris(Arrays.asList(finishURI))
                    .setTokenUri(c.token_uri);
            clientSecrets = new GoogleClientSecrets().setInstalled(details);

        }
    }

    public static com.google.api.client.auth.oauth2.Credential authorize(String token) throws IOException {
        // Load client secrets.
        initGoogle();
            flow =
                    new GoogleAuthorizationCodeFlow.Builder(
                            HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                            .setDataStoreFactory(DATA_STORE_FACTORY)
                            .build();

            com.google.api.client.auth.oauth2.Credential credential = flow.loadCredential(token);

            return credential;

    }

    private static HttpRequestInitializer setHttpTimeout(final HttpRequestInitializer requestInitializer)  {
        return new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest httpRequest) {
                try{
                requestInitializer.initialize(httpRequest);
                httpRequest.setConnectTimeout(3 * 60000);  // 3 minutes connect timeout
                httpRequest.setReadTimeout(3 * 60000);  // 3 minutes read timeout
                }catch(IOException e){
                    System.out.println("******IOException********");
                    //e.printStackTrace();
                }catch(NullPointerException e){
                    System.out.println("******NullPointerException********");
                    //e.printStackTrace();
                }
            }
        };
    }

    public static Drive getDriveService(String token) throws IOException {
        com.google.api.client.auth.oauth2.Credential credential = authorize(token);
        if(credential!=null){
            return new Drive.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, setHttpTimeout(credential))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        }else {
            return null;
        }
    }

    public  OAuthCredential updateToken(){
        //Updating the access token for googledrive using refresh token
        OAuthCredential cred = (OAuthCredential)credential;
        try{
            System.out.println("\nOld AccessToken: "+cred.token+"\n"+cred.refreshToken);
            GoogleDriveOauthService.GoogleDriveConfig c = new GoogleDriveOauthService.GoogleDriveConfig();
            GoogleCredential refreshTokenCredential = new GoogleCredential.Builder().setJsonFactory(JSON_FACTORY).setTransport(HTTP_TRANSPORT).setClientSecrets(c.client_id, c.client_secret).build().setRefreshToken(cred.refreshToken);
            if(refreshTokenCredential.refreshToken()){
                System.out.println("REFRESHTOKEN!");
            }
            String accessToken = refreshTokenCredential.getAccessToken();
            cred.token = accessToken;

            TokenResponse tr = new TokenResponse();
            tr.setAccessToken(refreshTokenCredential.getAccessToken());
            tr.setRefreshToken(refreshTokenCredential.getRefreshToken());

            tr.setExpiresInSeconds(refreshTokenCredential.getExpiresInSeconds());
            tr.setScope(refreshTokenCredential.getServiceAccountScopesAsString());

            flow.createAndStoreCredential(tr,accessToken);


            System.out.println("New AccessToken:"+cred.token+" RefreshToken:"+cred.refreshToken);
        }catch (IOException e){
            e.printStackTrace();
        }

        return cred;
    }
}
