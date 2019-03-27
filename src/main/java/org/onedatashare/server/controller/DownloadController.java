package org.onedatashare.server.controller;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.onedatashare.server.model.error.AuthenticationRequired;
import org.onedatashare.server.model.useraction.UserAction;
import org.onedatashare.server.service.DbxService;
import org.onedatashare.server.service.ResourceServiceImpl;
import org.onedatashare.server.service.VfsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.stream.IntStream;


@RestController
public class DownloadController {

    @Autowired
    private DbxService dbxService;

    @Autowired
    private VfsService vfsService;

    @Autowired
    private ResourceServiceImpl resourceService;

    @PostMapping
    public Object download(@RequestHeader HttpHeaders headers, @RequestBody UserAction userAction) {
        String cookie = headers.getFirst("cookie");
//        System.out.println(userAction);

        String stream = "anything";
        if (userAction.uri.startsWith("dropbox://")) {
            return dbxService.getDownloadURL(cookie, userAction);
        } else if ("googledrive:/".equals(userAction.type)) {
            if (userAction.credential == null) {
                return new ResponseEntity<>(new AuthenticationRequired("oauth"), HttpStatus.INTERNAL_SERVER_ERROR);
            } else return resourceService.download(cookie, userAction);
        } else if (userAction.uri.startsWith("ftp://")) {
            return vfsService.getDownloadURL(cookie, userAction);
        } else if (userAction.uri.startsWith("sftp://")) {
            return Mono.just("/api/stork/sftp_download/file");
        }
        return null;
    }
}
