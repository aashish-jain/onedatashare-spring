package org.onedatashare.server.service;

import org.onedatashare.module.globusapi.GlobusClient;
import org.onedatashare.server.model.core.*;
import org.onedatashare.server.model.credential.GlobusWebClientCredential;
import org.onedatashare.server.model.credential.OAuthCredential;
import org.onedatashare.server.model.credential.UploadCredential;
import org.onedatashare.server.model.credential.UserInfoCredential;
import org.onedatashare.server.model.error.TokenExpiredException;
import org.onedatashare.server.model.useraction.IdMap;
import org.onedatashare.server.model.useraction.UserAction;
import org.onedatashare.server.model.useraction.UserActionResource;
import org.onedatashare.server.module.clientupload.ClientUploadSession;
import org.onedatashare.server.module.dropbox.DbxSession;
import org.onedatashare.server.module.googledrive.GoogleDriveSession;
import org.onedatashare.server.module.gridftp.GridftpSession;
import org.onedatashare.server.module.http.HttpSession;
import org.onedatashare.server.module.vfs.VfsSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ResourceServiceImpl implements ResourceService<Resource>  {
    @Autowired
    private UserService userService;

    @Autowired
    private JobService jobService;

    private HashMap<UUID, Disposable> ongoingJobs = new HashMap<>();

    public Mono<? extends Resource> getResourceWithUserActionUri(String cookie, UserAction userAction) {
        final String path = pathFromUri(userAction.getUri());
        String id = userAction.getId();
        ArrayList<IdMap> idMap = userAction.getMap();
        return userService.getLoggedInUser(cookie)
                .map(User::getCredentials)
                .map(uuidCredentialMap -> uuidCredentialMap.get(UUID.fromString(userAction.getCredential().getUuid())))
                .map(credential -> new GoogleDriveSession(URI.create(userAction.getUri()), credential))
                .flatMap(GoogleDriveSession::initialize)
                .flatMap(driveSession -> driveSession.select(path,id, idMap))
                .onErrorResume(throwable -> throwable instanceof TokenExpiredException, throwable ->
                    Mono.just(userService.updateCredential(cookie,((TokenExpiredException)throwable).cred))
                            .map(credential -> new GoogleDriveSession(URI.create(userAction.getUri()), credential))
                            .flatMap(GoogleDriveSession::initialize)
                            .flatMap(driveSession -> driveSession.select(path,id, idMap))
                );
    }

    public Mono<Resource> getResourceWithUserActionResource(String cookie, UserActionResource userActionResource) {
        final String path = pathFromUri(userActionResource.getUri());
        String id = userActionResource.getId();
        ArrayList<IdMap> idMap = userActionResource.getMap();

        return userService.getLoggedInUser(cookie)
                .map(user -> createCredential(userActionResource, user))
                .map(credential -> createSession(userActionResource.getUri(), credential))
                .flatMap(session -> session.initialize())
                .flatMap(session -> ((Session)session).select(path,id,idMap));
    }

    public String pathFromUri(String uri) {
        String path = "";
        if(uri.contains(ODSConstants.DROPBOX_URI_SCHEME))
            path = uri.substring(ODSConstants.DROPBOX_URI_SCHEME.length() - 1);
        else if(uri.contains(ODSConstants.DRIVE_URI_SCHEME))
            path = uri.substring(ODSConstants.DRIVE_URI_SCHEME.length() - 1);
        else
            path = uri;

        try {
            path = java.net.URLDecoder.decode(path, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return path;
    }

    public Credential createCredential(UserActionResource userActionResource, User user) {
        if(userActionResource.getUri().contains(ODSConstants.DROPBOX_URI_SCHEME) ||
                userActionResource.getUri().contains(ODSConstants.DRIVE_URI_SCHEME)){
            return user.getCredentials().get(UUID.fromString(userActionResource.getCredential().getUuid()));
        }else if(userActionResource.getUri().equals(ODSConstants.UPLOAD_IDENTIFIER)){
            return userActionResource.getUploader();
        }else if(userActionResource.getUri().startsWith(ODSConstants.GRIDFTP_URI_SCHEME)){

            GlobusClient gc = userService.getGlobusClientFromUser(user);
            return new GlobusWebClientCredential(userActionResource.getCredential().getGlobusEndpoint(), gc);
        }
        else return new UserInfoCredential(userActionResource.getCredential());
    }


    public Session createSession(String uri, Credential credential) {
        if(uri.contains(ODSConstants.DROPBOX_URI_SCHEME))
            return new DbxSession(URI.create(uri), credential);
        else if(uri.contains(ODSConstants.UPLOAD_IDENTIFIER)) {
            UploadCredential upc = (UploadCredential) credential;
            return new ClientUploadSession(upc.getFux(), upc.getSize(), upc.getName());
        }
        else if(uri.contains(ODSConstants.DRIVE_URI_SCHEME))
            return new GoogleDriveSession(URI.create(uri), credential);
        else if(credential instanceof GlobusWebClientCredential)
            return new GridftpSession(URI.create(uri), credential);
        else if(uri.startsWith(ODSConstants.HTTPS_URI_SCHEME) || uri.startsWith(ODSConstants.HTTP_URI_SCHEME))
            return new HttpSession(URI.create(uri));
        else return new VfsSession(URI.create(uri), credential);
    }

    public Mono<Stat> list(String cookie, UserAction userAction) {
        return getResourceWithUserActionUri(cookie, userAction).flatMap(Resource::stat);
    }

    public Mono<Stat> mkdir(String cookie, UserAction userAction) {
        return getResourceWithUserActionUri(cookie, userAction)
                .flatMap(Resource::mkdir)
                .flatMap(resource -> ((Resource)resource).stat());
    }

    public Mono<Resource> delete(String cookie, UserAction userAction) {
        return getResourceWithUserActionUri(cookie, userAction)
                .flatMap(Resource::delete);
    }

    public Mono<Job> submit(String cookie, UserAction userAction) {
        return userService.getLoggedInUser(cookie)
            .map(user -> {
                Job job = new Job(userAction.getSrc(), userAction.getDest());
                job.setStatus(JobStatus.scheduled);
                job = user.saveJob(job);
                userService.saveUser(user).subscribe();
                return job;
            })
            .flatMap(jobService::saveJob)
            .doOnSuccess(job -> processTransferFromJob(job, cookie))
            .subscribeOn(Schedulers.elastic());
    }

    //@Override
    public Mono<String> download(String cookie, UserAction userAction) {
        return getResourceWithUserActionUri(cookie, userAction)
                .flatMap(Resource::download);
    }

    public Mono<Job> restartJob(String cookie, UserAction userAction){
        return userService.getLoggedInUser(cookie)
            .flatMap(user ->{
                return jobService.findJobByJobId(cookie, userAction.getJob_id())
                    .flatMap(job -> {
                        Job restartedJob = new Job(job.getSrc(), job.getDest());
                        boolean credsExists = updateJobCredentials(user, job);
                        if(!credsExists){
                            return Mono.error(new Exception("Restart job failed since either or both credentials of the job do not exist"));
                        }
                        restartedJob.setStatus(JobStatus.scheduled);
                        restartedJob.setRestartedJob(true);
                        restartedJob.setSourceJob(userAction.getJob_id());
                        restartedJob = user.saveJob(restartedJob);
                        userService.saveUser(user).subscribe();
                        return Mono.just(restartedJob);
                    })
                    .flatMap(jobService::saveJob)
                    .doOnSuccess(restartedJob -> processTransferFromJob(restartedJob, cookie));
            })
            .subscribeOn(Schedulers.elastic());
    }

    public Mono<Job> deleteJob(String cookie, UserAction userAction){
        return jobService.findJobByJobId(cookie,userAction.getJob_id())
                .map(job -> {
                    job.setDeleted(true);
                    return job;
                }).flatMap(jobService::saveJob).subscribeOn(Schedulers.elastic());
    }

    /**
     * This method cancel an ongoing transfer.
     * User email and job id passed in the request is used to obtain the job UUID,
     * which is in turn used to access the ongoing job flux from the ongoingJobs map.
     *
     * This flux is then disposed and the job is evicted from the map to cancel the transfer.
     *
     * @param cookie
     * @param userAction
     * @return Mono of job that was stopped
     */
    public Mono<Job> cancel(String cookie, UserAction userAction) {
        return userService.getLoggedInUser(cookie)
                .flatMap((User user) -> jobService.findJobByJobId(cookie, userAction.getJob_id())
                        .map(job -> {
                            ongoingJobs.get(job.getUuid()).dispose();
                            ongoingJobs.remove(job.getUuid());
                            return job.setStatus(JobStatus.removed);
                        }))
                .subscribeOn(Schedulers.elastic());
    }

    public boolean updateJobCredentials(User user, Job restartedJob){
        boolean credsExist = true;
        if(restartedJob.getSrc().getCredential() != null) {
            UUID srcCredUUID = getCredUuidUsingCredName(user, restartedJob.getSrc().getCredential().getName());
            if(srcCredUUID != null){
                if(!UUID.fromString(restartedJob.getSrc().getCredential().getUuid()).equals(srcCredUUID)){
                    restartedJob.getSrc().getCredential().setUuid(srcCredUUID.toString());
                }
            }
            else
                credsExist = false;
        }

        if(!credsExist)
            return credsExist;    // don't want to check for dest cred if src cred doesn't exist

        if(restartedJob.getDest().getCredential() != null) {
            UUID destCredUUID = getCredUuidUsingCredName(user, restartedJob.getDest().getCredential().getName());
            if(destCredUUID != null){
                if(!UUID.fromString(restartedJob.getDest().getCredential().getUuid()).equals(destCredUUID)){
                    restartedJob.getDest().getCredential().setUuid(destCredUUID.toString());
                }
            }
            else
                credsExist = false;
        }

        return credsExist;
    }

    public UUID getCredUuidUsingCredName(User user, String credName){
        for(Map.Entry<UUID, Credential> userCredEntry : user.getCredentials().entrySet()){
            if(userCredEntry.getValue() instanceof OAuthCredential){
                OAuthCredential cred = (OAuthCredential) userCredEntry.getValue();
                if(cred.getName().equals(credName)){
                    return  userCredEntry.getKey();
                }
            }
        }
        return null;
    }

    public void processTransferFromJob(Job job, final String cookie) {
        Transfer<Resource, Resource> transfer = new Transfer<>();
        Disposable ongoingJob = getResourceWithUserActionResource(cookie, job.getSrc())
                .map(transfer::setSource)
            .flatMap(t -> getResourceWithUserActionResource(cookie, job.getDest()))
            .map(transfer::setDestination)
            .flux()
            .flatMap(transfer1 -> transfer1.start(1L << 20))
            .doOnSubscribe(s -> job.setStatus(JobStatus.processing))
            .doOnCancel(new RunnableCanceler(job))
            .doFinally(s -> {
                if (job.getStatus() != JobStatus.removed)
                    job.setStatus(JobStatus.complete);
                jobService.saveJob(job).subscribe();
                ongoingJobs.remove(job.getUuid());
            })
            .map(job::updateJobWithTransferInfo)
            .flatMap(jobService::saveJob)
            .subscribe();
        ongoingJobs.put(job.getUuid(), ongoingJob);
    }

    class RunnableCanceler implements Runnable {
        Job job;

        public RunnableCanceler(Job job) {
            this.job = job;
        }

        @Override
        public void run() {
            job.setStatus(JobStatus.removed);
        }
    }
}
