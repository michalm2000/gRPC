import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class GRPCServer {
    public static void main(String[] args) {
        int port = 50001;
        System.out.println("Starting server..");
        Server server = ServerBuilder.forPort(port).addService(new GrpcServerImpl()).build();
        try{
            server.start();
            System.out.println("...Server started");
            server.awaitTermination();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    static class GrpcServerImpl extends GrpcServiceGrpc.GrpcServiceImplBase {
        private static final Path SERVER_BASE_PATH = Paths.get("C:\\Users\\michaldes\\IdeaProjects\\gRPC\\gRPCServer\\src\\main\\resources\\Images");
        private List<CD> cdList;
        private List<String> songText;

        public GrpcServerImpl() {
            this.cdList = new ArrayList<>();
            songText = loadSongText();
        }

        private List<String> loadSongText() {
            try {
                BufferedReader reader = new BufferedReader(new FileReader("C:\\Users\\michaldes\\IdeaProjects\\gRPC\\gRPCServer\\src\\main\\resources\\song.txt"));
                return reader.lines().toList();
            } catch (IOException e ){
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public void saveCD(CD request, StreamObserver<GrpcResponse> responseObserver) {
            cdList.add(request);
            GrpcResponse response = GrpcResponse.newBuilder().setMessage("Added CD to collection").build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void getCDbyTitle(GrpcResponse request, StreamObserver<CD> responseObserver) {
            CD cd = cdList.stream().filter(c -> c.getTitle().equals(request.getMessage())).findFirst().orElse(null);
            responseObserver.onNext(cd);
            responseObserver.onCompleted();
        }

        @Override
        public void getCDs(Empty request, StreamObserver<CDs> responseObserver) {
            CDs cds  = CDs.newBuilder().addAllCds(cdList).build();
            responseObserver.onNext(cds);
            responseObserver.onCompleted();
        }

        @Override
        public void grpcProcedure(GrpcRequest request, StreamObserver<GrpcResponse> responseObserver) {
            String msg;
            System.out.println("... called GrpcProcedure");
            if (request.getAge() >= 18){
                msg = "Mr/Ms " + request.getName();
            }
            else {
                msg = "Boy/Girl";
            }
            GrpcResponse response = GrpcResponse.newBuilder().setMessage("hello" + msg).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }


        @Override
        public StreamObserver<SongLineGetter> getSongText(StreamObserver<SongLine> responseObserver) {
            return  new StreamObserver<SongLineGetter>() {
                int count = 0;
                @Override
                public void onNext(SongLineGetter songLineGetter) {
                    if (count >= songText.size()) responseObserver.onError(new IndexOutOfBoundsException());
                    responseObserver.onNext(SongLine.newBuilder().setLine(songText.get(songLineGetter.getLineNumber() - 1))
                            .setLineNumber(songLineGetter.getLineNumber()).setAllLines(songText.size()).build());
                    count++;
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }

        @Override
        public void getCDsAsync(Empty request, StreamObserver<CDs> responseObserver) {
            try {
                Thread.sleep(5000);

            CDs cds  = CDs.newBuilder().addAllCds(cdList).build();
            responseObserver.onNext(cds);
            responseObserver.onCompleted();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public StreamObserver<FileUploadRequest> upload(StreamObserver<FileUploadResponse> responseObserver) {
            return new StreamObserver<FileUploadRequest>() {
                OutputStream writer;
                Status status = Status.IN_PROGRESS;
                @Override
                public void onNext(FileUploadRequest fileUploadRequest) {
                    try{
                        if(fileUploadRequest.hasMetadata()){
                            writer = getFilePath(fileUploadRequest);
                        }else{
                            writeFile(writer, fileUploadRequest.getFile().getContent());
                            FileUploadResponse response = FileUploadResponse.newBuilder().setStatus(status).build();
                            responseObserver.onNext(response);
                        }
                    }catch (IOException e){
                        this.onError(e);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    status = Status.FAILED;
                    this.onCompleted();
                }

                @Override
                public void onCompleted() {
                    closeFile(writer);
                    status = Status.IN_PROGRESS.equals(status) ? Status.SUCCESS : status;
                    FileUploadResponse response = FileUploadResponse.newBuilder()
                            .setStatus(status)
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                }
                private OutputStream getFilePath(FileUploadRequest request) throws IOException {
                    var fileName = request.getMetadata().getName() + "." + request.getMetadata().getType();
                    return Files.newOutputStream(SERVER_BASE_PATH.resolve(fileName), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }

                private void writeFile(OutputStream writer, ByteString content) throws IOException {
                    writer.write(content.toByteArray());
                    writer.flush();
                }

                private void closeFile(OutputStream writer){
                    try {
                        writer.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

        }
    }


}
