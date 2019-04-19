package org.onedatashare.server.model.core;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.onedatashare.module.globusapi.Result;
import org.onedatashare.server.model.util.Progress;
import org.onedatashare.server.model.util.Throughput;
import org.onedatashare.server.model.util.Time;
import org.onedatashare.server.model.util.TransferInfo;
import org.onedatashare.server.module.gridftp.GridftpResource;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@NoArgsConstructor
@Data
public class Transfer<S extends Resource, D extends Resource> {
  public S source;
  public D destination;

  /** Periodically updated information about the ongoing transfer. */
  public final TransferInfo info = new TransferInfo();

  // Timer counts 0.0 for files with very small size
  protected Time timer;
  protected Progress progress = new Progress();
  protected Throughput throughput = new Throughput();

  public Transfer(S source, D destination) {
    this.source = source;
    this.destination = destination;
  }

  public Flux<TransferInfo> start(Long sliceSize) {

    if (source instanceof GridftpResource && destination instanceof GridftpResource){
        ((GridftpResource) source).transferTo(((GridftpResource) destination)).subscribe();
        return Flux.empty();
    }else if (source instanceof GridftpResource || destination instanceof GridftpResource){
        return Flux.error(new Exception("Can not send from GridFTP to other protocols"));
    }

    // sliceSize = (sliceSize == null) ? 1024L : sliceSize;

    // initialize();
    Tap tap = source.tap();
    Stat tapStat = tap.getTransferStat();

    if(tapStat == null) {
      System.out.println("Error occurred while generating tap stat object");
      return null;
    }
    info.setTotal(tapStat.size);

    return Flux.fromIterable(tapStat.getFilesList())
            .doOnSubscribe(s -> startTimer())
            .flatMap(fileStat -> {
              final Drain drain;
              if(tapStat.isDir())
                drain = destination.sink(fileStat);
              else
                drain = destination.sink();
              return tap.tap(fileStat, sliceSize)
                      .subscribeOn(Schedulers.elastic())
                      .doOnNext(drain::drain)
                      .subscribeOn(Schedulers.elastic())
                      .map(this::addProgress)
                      .doOnComplete(drain::finish);
            }).doFinally(s -> done());
  }

  public void initialize() {
    Stat stat = (Stat) source.stat().block();
    info.setTotal(stat.size);
  }

  public void initializeUpload(int fileSize){
    info.setTotal(fileSize);
  }

  public void done() {
    timer.stop();
  }

  public void startTimer() {
    timer = new Time();
  }

  public TransferInfo addProgress(Slice slice) {
    long size = slice.length();
    progress.add(size);
    throughput.update(size);
    info.update(timer, progress, throughput);
    return info;
  }

  public Transfer<S, D> setSource(S source) {
    this.source = source;
    return this;
  }

  public Transfer<S, D> setDestination(D destination) {
    this.destination = destination;
    return this;
  }
}
